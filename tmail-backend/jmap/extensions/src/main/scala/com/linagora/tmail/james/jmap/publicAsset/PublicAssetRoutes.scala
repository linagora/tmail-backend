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

package com.linagora.tmail.james.jmap.publicAsset

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.stream
import java.util.stream.Stream

import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRoutes.{BUFFER_SIZE, LOGGER, ONE_YEAR_AS_SECONDS}
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames.{CACHE_CONTROL, CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.jmap.core.ProblemDetails
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.model._
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.compat.java8.FunctionConverters._
import scala.util.{Success, Try}

object PublicAssetRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[PublicAssetRoutes])
  val ONE_YEAR_AS_SECONDS: Int = 31536000
  val BUFFER_SIZE: Int = 16 * 1024
}

class PublicAssetRoutes @Inject()(val publicAssetRepository: PublicAssetRepository) extends JMAPRoutes {

  private val usernameParam: String = "username"
  private val assetIdParam: String = "assetId"
  private val publicAssetUri = s"/publicAsset/{$usernameParam}/{$assetIdParam}"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, publicAssetUri))
      .action(this.getAsset)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, publicAssetUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def getAsset(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] = {
    val usernameTry: Try[Username] = Try(Username.of(request.param(usernameParam)))
    val assetIdTry: Try[PublicAssetId] = PublicAssetId.fromString(request.param(assetIdParam))

    (usernameTry, assetIdTry) match {
      case (Success(username), Success(assetId)) => getAsset(request, response, username, assetId)
      case _ => returnNotFound(request, response)
          .asJava()
          .`then`
    }
  }

  private def getAsset(request: HttpServerRequest, response: HttpServerResponse, username: Username, assetId: PublicAssetId): Mono[Void] =
    SMono(publicAssetRepository.get(username, assetId))
      .switchIfEmpty(SMono.error(PublicAssetNotFoundException(assetId)))
      .flatMap(asset => downloadAsset(response = response,
        contentType = ContentType.of(asset.contentType.value),
        content = asset.content.apply(),
        size = asset.size))
      .onErrorResume {
        case _: PublicAssetNotFoundException => returnNotFound(request, response)
        case e =>
          LOGGER.error("Unexpected error upon downloading public asset {}", request.uri(), e)
          respondDetails(response, ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage), INTERNAL_SERVER_ERROR)
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .asJava()
      .`then`

  private def returnNotFound(request: HttpServerRequest, response: HttpServerResponse): SMono[Unit] = {
    LOGGER.info("Can not serve not found public asset {}", request.uri())
    respondDetails(response, ProblemDetails(status = NOT_FOUND, detail = "The public asset could not be found"), NOT_FOUND)
  }

  private def downloadAsset(response: HttpServerResponse,
                            contentType: ContentType,
                            content: InputStream,
                            size: Size): SMono[Unit] = {
    val resourceSupplier: Callable[InputStream] = () => content
    val sourceSupplier: java.util.function.Function[InputStream, Mono[Void]] = stream => SMono(addContentLengthHeader(size)
      .apply(response)
      .header(CONTENT_TYPE, contentType.asString)
      .header(CACHE_CONTROL, s"immutable, max-age=$ONE_YEAR_AS_SECONDS")
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

  private def addContentLengthHeader(size: Size): HttpServerResponse => HttpServerResponse =
    resp => resp.header(CONTENT_LENGTH, size.value.toString)

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails, statusCode: HttpResponseStatus = BAD_REQUEST): SMono[Unit] =
    SMono.fromCallable(() => ResponseSerializer.serialize(details))
      .map(Json.stringify)
      .map(_.getBytes(StandardCharsets.UTF_8))
      .flatMap(bytes =>
        SMono.fromPublisher(httpServerResponse.status(statusCode)
            .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
            .header(CONTENT_LENGTH, Integer.toString(bytes.length))
            .sendByteArray(SMono.just(bytes))
            .`then`)
          .`then`)
}
