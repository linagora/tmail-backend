/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.routes

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.stream.Stream
import java.util.{UUID, stream}

import com.linagora.tmail.james.jmap.blob.{UnauthenticatedBlobDownloadToken, UnauthenticatedBlobDownloadTokenRepository}
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus.{INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValidationUtil, HttpMethod, QueryStringDecoder}
import jakarta.inject.Inject
import org.apache.james.blob.api.{BlobId => BlobStoreBlobId}
import org.apache.james.core.Username
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.api.model.{AccountId => JavaAccountId}
import org.apache.james.jmap.core.{AccountId, Id, ProblemDetails}
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.mail.{BlobId => JmapBlobId}
import org.apache.james.jmap.routes.DownloadRoutes.BUFFER_SIZE
import org.apache.james.jmap.routes.{Blob, BlobNotFoundException, BlobResolvers}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.{MailboxSession, SessionProvider}
import org.apache.james.metrics.api.{Metric, MetricFactory}
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.{Failure, Success, Try}

object UnauthenticatedBlobAccessDownloadRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[UnauthenticatedBlobAccessDownloadRoutes])

  def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Unit] =
    if (httpServerResponse.hasSentHeaders) {
      // Response already committed (e.g. an error occurred while streaming the blob body):
      // status and headers are already on the wire, so we cannot rewrite them into an error response.
      // Attempting to do so would throw `IllegalStateException: Status and headers already sent`, masking
      // the original error. The real cause is already logged by the caller; let the connection be closed.
      SMono.empty
    } else {
      SMono.fromCallable(() => ResponseSerializer.serialize(details))
        .map(Json.stringify)
        .map(_.getBytes(StandardCharsets.UTF_8))
        .flatMap(bytes =>
          SMono.fromPublisher(httpServerResponse.status(details.status)
            .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
            .header(CONTENT_LENGTH, Integer.toString(bytes.length))
            .sendByteArray(SMono.just(bytes))
            .`then`).`then`)
    }
}

case class InvalidUnauthenticatedBlobAccessTokenException() extends RuntimeException

case class ValidatedUnauthenticatedBlobDownload(jmapBlobId: JmapBlobId,
                                                username: Username)

class UnauthenticatedBlobAccessDownloadRoutes @Inject()(val tokenRepository: UnauthenticatedBlobDownloadTokenRepository,
                                                        val blobResolvers: BlobResolvers,
                                                        val blobIdFactory: BlobStoreBlobId.Factory,
                                                        val sessionProvider: SessionProvider,
                                                        val metricFactory: MetricFactory) extends JMAPRoutes {
  import UnauthenticatedBlobAccessDownloadRoutes.{LOGGER, respondDetails}

  private val accountIdParam: String = "accountId"
  private val blobIdParam: String = "blobId"
  private val tokenParam: String = "token"
  private val unauthenticatedDownloadUri = s"/unauthenticatedDownload/{$accountIdParam}/{$blobIdParam}"
  private val pendingDownloadMetric: Metric = metricFactory.generate("jmap_pending_unauthenticated_blob_downloads")

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, unauthenticatedDownloadUri))
      .action(this.get)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, unauthenticatedDownloadUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def get(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    validateToken(request)
      .flatMap(validated => resolveSession(validated.username)
        .flatMap(session => blobResolvers.resolve(validated.jmapBlobId, session))
        .flatMap(blob => downloadBlob(response, blob)
          .doOnSubscribe(_ => pendingDownloadMetric.increment())
          .doFinally(_ => pendingDownloadMetric.decrement())
          .`then`()))
      .onErrorResume {
        case _: InvalidUnauthenticatedBlobAccessTokenException =>
          respondDetails(response, ProblemDetails(status = UNAUTHORIZED, detail = "Invalid token"))
        case _: BlobNotFoundException =>
          respondDetails(response, ProblemDetails(status = NOT_FOUND, detail = "The resource could not be found"))
        case e =>
          LOGGER.error("Unexpected error upon unauthenticated blob download for accountId={} blobId={}",
            Option(request.param(accountIdParam)).getOrElse(""),
            Option(request.param(blobIdParam)).getOrElse(""),
            e)
          respondDetails(response, ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = "Internal server error"))
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .asJava()
      .`then`

  private def validateToken(request: HttpServerRequest): SMono[ValidatedUnauthenticatedBlobDownload] =
    SMono.fromCallable(() => parseRequest(request))
      .flatMap {
        case (javaAccountId, blobStoreBlobId, jmapBlobId, token) =>
          SMono(tokenRepository.check(javaAccountId, blobStoreBlobId, token))
            .flatMap(optionalUsername => optionalUsername.toScala
              .map(username => SMono.fromCallable(() => ValidatedUnauthenticatedBlobDownload(jmapBlobId, username)))
              .getOrElse(SMono.error(InvalidUnauthenticatedBlobAccessTokenException())))
      }

  private def parseRequest(request: HttpServerRequest): (JavaAccountId, BlobStoreBlobId, JmapBlobId, UnauthenticatedBlobDownloadToken) = {
    val parsedRequest: Try[(JavaAccountId, BlobStoreBlobId, JmapBlobId, UnauthenticatedBlobDownloadToken)] = for {
      accountId <- Id.validate(request.param(accountIdParam))
        .fold(_ => Failure(InvalidUnauthenticatedBlobAccessTokenException()), id => Success(AccountId(id)))
      blobIdAsString = request.param(blobIdParam)
      blobStoreBlobId <- Try(blobIdFactory.parse(blobIdAsString))
      jmapBlobId <- JmapBlobId.of(blobIdAsString)
        .fold(_ => Failure(InvalidUnauthenticatedBlobAccessTokenException()), blobId => Success(blobId))
      token <- queryParam(request, tokenParam)
        .map(value => Try(new UnauthenticatedBlobDownloadToken(UUID.fromString(value))))
        .getOrElse(Failure(InvalidUnauthenticatedBlobAccessTokenException()))
    } yield (JavaAccountId.fromString(accountId.id.value), blobStoreBlobId, jmapBlobId, token)

    parsedRequest
      .getOrElse(throw InvalidUnauthenticatedBlobAccessTokenException())
  }

  private def resolveSession(username: Username): SMono[MailboxSession] =
    SMono.fromCallable(() => sessionProvider.createSystemSession(username))

  private def downloadBlob(response: HttpServerResponse, blob: Blob): SMono[Unit] = {
    val resourceSupplier: Callable[InputStream] = () => blob.content
    val sourceSupplier: java.util.function.Function[InputStream, Mono[Void]] = stream => SMono(addContentLengthHeader(blob.size)
      .compose(addCacheControlHeader())
      .apply(response)
      .header(CONTENT_TYPE, sanitizeHeaderValue(blob.contentType.asString))
      .status(OK)
      .send(ReactorUtils.toChunks(stream, BUFFER_SIZE)
        .map(Unpooled.wrappedBuffer(_))
        .subscribeOn(Schedulers.boundedElastic()))).asJava()
    val resourceRelease: Consumer[InputStream] = (stream: InputStream) => stream.close()

    SMono.fromPublisher(Mono.using(
        resourceSupplier,
        sourceSupplier,
        resourceRelease))
      .`then`
  }

  private def sanitizeHeaderValue(value: String): String =
    if (HttpHeaderValidationUtil.validateValidHeaderValue(value) == -1) {
      value
    } else {
      "application/octet-stream"
    }

  private def addContentLengthHeader(sizeTry: Try[Size]): HttpServerResponse => HttpServerResponse =
    resp => sizeTry
      .map(size => resp.header("Content-Length", size.value.toString))
      .getOrElse(resp)

  private def addCacheControlHeader(): HttpServerResponse => HttpServerResponse =
    resp => resp.header(HttpHeaderNames.CACHE_CONTROL, "private, immutable, max-age=31536000")

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[String] =
    Option(new QueryStringDecoder(httpRequest.uri).parameters.get(parameterName))
      .toList
      .flatMap(_.asScala)
      .headOption
}
