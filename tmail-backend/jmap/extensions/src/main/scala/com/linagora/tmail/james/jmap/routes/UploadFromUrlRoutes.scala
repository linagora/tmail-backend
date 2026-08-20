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

import java.nio.charset.StandardCharsets
import java.util.stream
import java.util.stream.Stream

import com.linagora.tmail.james.jmap.upload._
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_LOCATION, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus.{BAD_REQUEST, CREATED, FORBIDDEN, INTERNAL_SERVER_ERROR, TOO_MANY_REQUESTS, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpMethod, HttpResponseStatus}
import jakarta.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.api.model.UploadMetaData
import org.apache.james.jmap.api.upload.UploadService
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Id, JmapRfc8621Configuration, ProblemDetails, SessionTranslator}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.{ResponseSerializer, UploadSerializer}
import org.apache.james.jmap.method.AccountNotFoundException
import org.apache.james.jmap.routes.UploadRoutes.asBlobId
import org.apache.james.jmap.routes.{ForbiddenException, UploadResponse}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.ContentType
import org.apache.james.util.ReactorUtils
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.Json
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.util.Try

object UploadFromUrlRoutes {
  val LOGGER: Logger = LoggerFactory.getLogger(classOf[UploadFromUrlRoutes])
  val DEFAULT_CONTENT_TYPE: ContentType = ContentType.of("application/octet-stream")
}

class UploadFromUrlRoutes @Inject()(@Named(InjectionKeys.RFC_8621) authenticator: Authenticator,
                                    configuration: JmapRfc8621Configuration,
                                    uploadService: UploadService,
                                    serializer: UploadSerializer,
                                    sessionTranslator: SessionTranslator,
                                    remoteUrlPolicy: RemoteUrlPolicy,
                                    downloader: RemoteFileDownloader) extends JMAPRoutes {
  import UploadFromUrlRoutes._

  private val accountIdParam = "accountId"
  private val uploadUri = s"/upload-from-url/{$accountIdParam}"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.POST, uploadUri))
      .action(this.postWithCors)
      .noCorsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, uploadUri))
      .action(this.options)
      .noCorsHeaders)

  private def postWithCors(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    post(request, addCorsHeaders(response))

  private def options(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    addCorsHeaders(response).send().`then`()

  private[routes] def post(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(authenticatedSession => {
        delegateIfNeeded(request, authenticatedSession)
          .flatMap {
            case (session, accountId) => upload(request, response, session, accountId)
          }
          .onErrorResume(exception => handleAuthenticatedError(request, response, authenticatedSession.getUser, exception))
      })
      .onErrorResume {
        case exception: UnauthorizedException =>
          LOGGER.warn("Unauthorized upload-from-URL request", exception)
          respondDetails(exception.addHeaders(response), UNAUTHORIZED, "Authentication failed")
        case exception =>
          LOGGER.error("Unexpected error before authenticating upload-from-URL request", exception)
          respondDetails(response, INTERNAL_SERVER_ERROR, "The remote file could not be uploaded")
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .asJava()
      .`then`()

  private def handleAuthenticatedError(request: HttpServerRequest,
                                       response: HttpServerResponse,
                                       username: Username,
                                       exception: Throwable): SMono[Void] = exception match {
    case unauthorizedException: UnauthorizedException =>
      LOGGER.warn("Unauthorized upload-from-URL request by user {}", username.asString(), unauthorizedException)
      respondDetails(unauthorizedException.addHeaders(response), UNAUTHORIZED, "Authentication failed")
    case _: ForbiddenException | _: AccountNotFoundException =>
      respondDetails(response, FORBIDDEN, "Upload to this account is forbidden")
    case _: RemoteUploadInvalidRequestException =>
      LOGGER.info("Invalid upload-from-URL request by user {} for remote URL {}", username.asString(), remoteUrlForLogging(request))
      respondDetails(response, BAD_REQUEST, "The upload-from-URL request is invalid")
    case rejectedException: RemoteUrlRejectedException =>
      LOGGER.info("User {} requested upload from rejected remote URL {}", username.asString(), RemoteUrlForLogging.sanitize(rejectedException.requestedUrl))
      respondDetails(response, BAD_REQUEST, "The remote URL is not allowed")
    case tooLargeException: RemoteUploadTooLargeException =>
      LOGGER.info("Remote upload by user {} from URL {} exceeded the size limit of {} bytes; requested size is at least {}",
        username.asString(),
        remoteUrlForLogging(request),
        configuration.maxUploadSize.value.value,
        tooLargeException.bestKnownSize.map(size => s"$size bytes").getOrElse("unknown"))
      respondDetails(response, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "The remote file exceeds the upload size limit")
    case capacityException: RemoteUploadCapacityException =>
      LOGGER.warn("Remote upload capacity unavailable for user {} requesting URL {}", username.asString(), remoteUrlForLogging(request), capacityException)
      respondDetails(response, TOO_MANY_REQUESTS, "Remote upload capacity is unavailable")
    case remoteException @ (_: RemoteUploadTimeoutException | _: RemoteUpstreamException) =>
      LOGGER.warn("Remote upload failed for user {} requesting URL {}", username.asString(), remoteUrlForLogging(request), remoteException)
      respondDetails(response, INTERNAL_SERVER_ERROR, "The remote file could not be uploaded")
    case unexpectedException =>
      LOGGER.error("Unexpected upload-from-URL error for user {} requesting URL {}", username.asString(), remoteUrlForLogging(request), unexpectedException)
      respondDetails(response, INTERNAL_SERVER_ERROR, "The remote file could not be uploaded")
  }

  private def delegateIfNeeded(request: HttpServerRequest,
                               mailboxSession: MailboxSession): SMono[(MailboxSession, AccountId)] =
    Id.validate(request.param(accountIdParam)) match {
      case Right(id: Id) =>
        val accountId = AccountId(id)
        sessionTranslator.delegateIfNeeded(mailboxSession, accountId)
          .map(session => (session, accountId))
      case Left(_) => SMono.error(RemoteUploadInvalidRequestException())
    }

  private def upload(request: HttpServerRequest,
                     response: HttpServerResponse,
                     mailboxSession: MailboxSession,
                     accountId: AccountId): SMono[Void] =
    SMono.fromCallable(() => validateRequest(request))
      .flatMap(contentLocation => validateEmptyBody(request).map(_ => contentLocation))
      .map(remoteUrlPolicy.validate)
      .flatMap(remoteUrl => downloader.withDownloadedFile(remoteUrl,
        configuration.maxUploadSize.value.value) {
        downloadedFile => uploadContent(selectContentType(request, downloadedFile.contentType),
          downloadedFile,
          mailboxSession)
      })
      .map(metadata => toUploadResponse(metadata, accountId))
      .flatMap(uploadResponse => writeUploadResponse(response, uploadResponse))

  private def uploadContent(contentType: ContentType,
                            downloadedFile: DownloadedRemoteFile,
                            mailboxSession: MailboxSession): SMono[UploadMetaData] =
    SMono(uploadService.upload(downloadedFile.content, contentType, mailboxSession.getUser))

  private def validateRequest(request: HttpServerRequest): String =
    Option(request.requestHeaders().get(CONTENT_LOCATION))
      .map(_.trim)
      .filter(_.nonEmpty)
      .getOrElse(throw RemoteUploadInvalidRequestException())

  private def remoteUrlForLogging(request: HttpServerRequest): String =
    Option(request.requestHeaders().get(CONTENT_LOCATION))
      .map(_.trim)
      .filter(_.nonEmpty)
      .map(RemoteUrlForLogging.sanitize)
      .getOrElse("<missing>")

  private def validateEmptyBody(request: HttpServerRequest): SMono[Unit] =
    SMono(request.receive().asByteArray().hasElements())
      .flatMap(hasElements => if (hasElements.booleanValue()) {
        SMono.error(RemoteUploadInvalidRequestException())
      } else {
        SMono.just(())
      })

  private def selectContentType(request: HttpServerRequest, upstreamContentType: Option[String]): ContentType =
    parseContentType(Option(request.requestHeaders().get(CONTENT_TYPE)))
      .orElse(parseContentType(upstreamContentType))
      .getOrElse(DEFAULT_CONTENT_TYPE)

  private def parseContentType(value: Option[String]): Option[ContentType] =
    value.map(_.trim)
      .filter(_.nonEmpty)
      .flatMap(contentType => Try(ContentType.MimeType.of(contentType))
        .map(_ => ContentType.of(contentType))
        .toOption)

  private def toUploadResponse(metadata: UploadMetaData, accountId: AccountId): UploadResponse =
    UploadResponse(accountId, asBlobId(metadata.uploadId), metadata.contentType, metadata.size)

  private def writeUploadResponse(response: HttpServerResponse, uploadResponse: UploadResponse): SMono[Void] = {
    val bytes = Json.stringify(serializer.serialize(uploadResponse)).getBytes(StandardCharsets.UTF_8)
    SMono.fromPublisher(response
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .header(CONTENT_LENGTH, Integer.toString(bytes.length))
      .status(CREATED)
      .sendByteArray(SMono.just(bytes))
      .`then`())
  }

  private def respondDetails(response: HttpServerResponse,
                             status: HttpResponseStatus,
                             detail: String): SMono[Void] = {
    val bytes = ResponseSerializer.serialize(ProblemDetails(status = status, detail = detail))
      .toString
      .getBytes(StandardCharsets.UTF_8)
    SMono.fromPublisher(response.status(status)
      .header(CONTENT_TYPE, JSON_CONTENT_TYPE)
      .header(CONTENT_LENGTH, Integer.toString(bytes.length))
      .sendByteArray(SMono.just(bytes))
      .`then`())
  }

  private def addCorsHeaders(response: HttpServerResponse): HttpServerResponse =
    response
      .header("Access-Control-Allow-Origin", "*")
      .header("Access-Control-Allow-Methods", "POST, OPTIONS")
      .header("Access-Control-Allow-Headers", "Content-Type, Content-Location, Authorization, Accept")
      .header("Access-Control-Max-Age", "86400")
}
