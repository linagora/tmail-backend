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

import com.google.common.base.CharMatcher
import com.google.common.collect.ImmutableList
import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.ZipUtil
import com.linagora.tmail.james.jmap.ZipUtil.ZipEntryStreamSource
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_DOWNLOAD_ALL
import com.linagora.tmail.james.jmap.routes.DownloadAllRoutes.{DEFAULT_FILE_NAME, ZIP_CONTENT_TYPE}
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaderNames.{CONTENT_LENGTH, CONTENT_TYPE}
import io.netty.handler.codec.http.HttpResponseStatus.{FORBIDDEN, INTERNAL_SERVER_ERROR, NOT_FOUND, OK, UNAUTHORIZED}
import io.netty.handler.codec.http.{HttpHeaderNames, HttpMethod, QueryStringDecoder}
import jakarta.inject.{Inject, Named}
import org.apache.james.jmap.HttpConstants.JSON_CONTENT_TYPE
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{AccountId, Capability, CapabilityFactory, CapabilityProperties, Id, ProblemDetails, SessionTranslator, URL, UrlPrefixes}
import org.apache.james.jmap.exceptions.UnauthorizedException
import org.apache.james.jmap.http.Authenticator
import org.apache.james.jmap.http.rfc8621.InjectionKeys
import org.apache.james.jmap.json.ResponseSerializer
import org.apache.james.jmap.method.AccountNotFoundException
import org.apache.james.jmap.routes.DownloadRoutes.LOGGER
import org.apache.james.jmap.routes.{AttachmentBlob, Blob, ForbiddenException}
import org.apache.james.jmap.{Endpoint, JMAPRoute, JMAPRoutes}
import org.apache.james.mailbox.model.{AttachmentId, AttachmentMetadata, FetchGroup, MessageId, MessageResult, ParsedAttachment}
import org.apache.james.mailbox.store.mail.AttachmentIdAssignationStrategy
import org.apache.james.mailbox.store.mail.model.impl.MessageParser
import org.apache.james.mailbox.{AttachmentManager, MailboxSession, MessageIdManager}
import org.apache.james.metrics.api.MetricFactory
import org.apache.james.mime4j.codec.EncoderUtil
import org.apache.james.mime4j.codec.EncoderUtil.Usage
import org.apache.james.util.ReactorUtils
import play.api.libs.json.{JsObject, Json}
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers
import reactor.netty.http.server.{HttpServerRequest, HttpServerResponse}

import scala.jdk.CollectionConverters._
import scala.util.Try

object DownloadAllRoutes {
  val DEFAULT_FILE_NAME = "noname"
  val ZIP_CONTENT_TYPE = "application/zip"
}

case class BlobWithName(blob: Blob, name: String)

case class MessageNotFoundException(id: String, cause: Throwable = null) extends RuntimeException(cause)

case class DownloadAllCapabilityProperties(endpoint: URL) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("endpoint" -> endpoint.value)
}

case class DownloadAllCapability(properties: DownloadAllCapabilityProperties,
                                 identifier: CapabilityIdentifier = LINAGORA_DOWNLOAD_ALL) extends Capability

class DownloadAllCapabilityFactory @Inject() extends CapabilityFactory {

  override def id(): CapabilityIdentifier = LINAGORA_DOWNLOAD_ALL

  override def create(urlPrefixes: UrlPrefixes): Capability =
    DownloadAllCapability(DownloadAllCapabilityProperties(URL(urlPrefixes.httpUrlPrefix.toString + "/downloadAll/{accountId}/{emailId}?name={name}")))
}

class DownloadAllRoutesModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
      .addBinding()
      .to(classOf[DownloadAllRoutes])

    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[DownloadAllCapabilityFactory])
  }
}

class DownloadAllRoutes @Inject()(@Named(InjectionKeys.RFC_8621) val authenticator: Authenticator,
                                  val sessionTranslator: SessionTranslator,
                                  val messageIdManager: MessageIdManager,
                                  val messageIdFactory: MessageId.Factory,
                                  val attachmentManager: AttachmentManager,
                                  val attachmentIdAssignationStrategy: AttachmentIdAssignationStrategy,
                                  val messageParser: MessageParser,
                                  val metricFactory: MetricFactory) extends JMAPRoutes {

  private val accountIdParam: String = "accountId"
  private val emailIdParam: String = "emailId"
  private val nameParam: String = "name"
  private val downloadUri = s"/downloadAll/{$accountIdParam}/{$emailIdParam}"

  override def routes(): stream.Stream[JMAPRoute] = Stream.of(
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.GET, downloadUri))
      .action(this.get)
      .corsHeaders,
    JMAPRoute.builder
      .endpoint(new Endpoint(HttpMethod.OPTIONS, downloadUri))
      .action(JMAPRoutes.CORS_CONTROL)
      .noCorsHeaders)

  private def get(request: HttpServerRequest, response: HttpServerResponse): Mono[Void] =
    SMono(authenticator.authenticate(request))
      .flatMap(mailboxSession => delegateIfNeeded(request, mailboxSession))
      .flatMap(session => get(request, response, session))
      .onErrorResume {
        case _: ForbiddenException =>
          LOGGER.warn("Attempt to download in another account")
          respondDetails(response, ProblemDetails(status = FORBIDDEN, detail = "You cannot download in others accounts"))
        case _: AccountNotFoundException =>
          LOGGER.info("Attempt to download with an invalid accountId")
          respondDetails(response, ProblemDetails(status = FORBIDDEN, detail = "You cannot download in others accounts"))
        case e: UnauthorizedException =>
          LOGGER.warn("Unauthorized", e)
          respondDetails(e.addHeaders(response), ProblemDetails(status = UNAUTHORIZED, detail = e.getMessage))
        case e: MessageNotFoundException =>
          LOGGER.info("Message not found: {}", e.id, e.cause)
          respondDetails(response, ProblemDetails(status = NOT_FOUND, detail = "The resource could not be found"))
        case e =>
          LOGGER.error("Unexpected error upon download {}", request.uri(), e)
          respondDetails(response, ProblemDetails(status = INTERNAL_SERVER_ERROR, detail = e.getMessage))
      }
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .asJava()
      .`then`

  private def delegateIfNeeded(request: HttpServerRequest, mailboxSession: MailboxSession): SMono[MailboxSession] = {
      Id.validate(request.param(accountIdParam)) match {
        case Right(id: Id) => sessionTranslator.delegateIfNeeded(mailboxSession, AccountId(id))
        case Left(throwable: Throwable) => SMono.error(throwable)
      }
  }

  private def get(request: HttpServerRequest, response: HttpServerResponse, mailboxSession: MailboxSession): SMono[Unit] = {
    val id: String = request.param(emailIdParam)
    SMono.fromCallable(() => messageIdFactory.fromString(id: String))
      .onErrorResume(e => SMono.error(MessageNotFoundException(id, e)))
      .flatMap(messageId => SFlux(messageIdManager.getMessagesReactive(ImmutableList.of(messageId), FetchGroup.FULL_CONTENT, mailboxSession))
        .singleOrEmpty()
        .switchIfEmpty(SMono.error(MessageNotFoundException(id)))
        .flatMapMany(messageResult => SFlux.fromIterable(messageResult.getLoadedAttachments.asScala)
          .filter(attachment => !attachment.isInline)
          .flatMap(attachment => getBlob(attachment.getAttachmentId, mailboxSession)
            .map(blob => BlobWithName(blob, attachment.getName.orElse(DEFAULT_FILE_NAME))))
          .switchIfEmpty(getAttachments(messageResult)
            .filter(attachment => !attachment.isInline)
            .flatMap(attachment => getBlob(attachment, messageId)
            .map(blob => BlobWithName(blob, attachment.getName.orElse(DEFAULT_FILE_NAME))))))
        .collectSeq()
        .flatMap(blobs => downloadBlobs(
            optionalName = queryParam(request, nameParam),
            response = response,
            blobs)
          .`then`()))
  }

  private def getAttachments(messageResult: MessageResult): SFlux[ParsedAttachment] =
    SFlux.fromIterable(messageParser.retrieveAttachments(messageResult.getFullContent.getInputStream)
      .getAttachments
      .asScala)

  private def getBlob(attachmentId: AttachmentId, mailboxSession: MailboxSession): SMono[Blob] =
    SMono(attachmentManager.getAttachmentReactive(attachmentId, mailboxSession))
      .flatMap(attachmentMetadata => SMono(attachmentManager.loadReactive(attachmentMetadata, mailboxSession))
        .map(content => AttachmentBlob(attachmentMetadata, content)))

  private def getBlob(attachment: ParsedAttachment, messageId: MessageId): SMono[Blob] =
    SMono.just(AttachmentBlob(AttachmentMetadata.builder
        .attachmentId(attachmentIdAssignationStrategy.assign(attachment, messageId))
        .`type`(attachment.getContentType)
        .size(attachment.getContent.size)
        .messageId(messageId)
      .build, attachment.getContent.openStream()))

  private def downloadBlobs(optionalName: Option[String],
                           response: HttpServerResponse,
                           blobs: Seq[BlobWithName]): SMono[Unit] = {
    SMono(addContentDispositionHeader(optionalName)
      .compose(addCacheControlHeader())
      .apply(response)
      .header(CONTENT_TYPE, ZIP_CONTENT_TYPE)
      .status(OK)
      .send(ZipUtil.createZipStream(blobs.map(blobWithName => new ZipEntryStreamSource(blobWithName.blob.content, blobWithName.name)).asJavaCollection)
        .map(Unpooled.wrappedBuffer(_))
        .subscribeOn(Schedulers.boundedElastic()))
      .`then`())
      .`then`()
  }

  private def addContentDispositionHeader(optionalName: Option[String]): HttpServerResponse => HttpServerResponse =
    resp => optionalName.map(addContentDispositionHeaderRegardingEncoding(_, resp))
      .getOrElse(resp)

  private def addCacheControlHeader(): HttpServerResponse => HttpServerResponse =
    resp => resp.header(HttpHeaderNames.CACHE_CONTROL, "private, immutable, max-age=31536000")

  private def addContentDispositionHeaderRegardingEncoding(name: String, resp: HttpServerResponse): HttpServerResponse =
    if (CharMatcher.ascii.matchesAllOf(name)) {
      Try(resp.header("Content-Disposition", "attachment; filename=\"" + name + "\""))
        // Can fail if the file name contains valid ascii character that are invalid in a contentDisposition header
        .getOrElse(resp.header("Content-Disposition", encodedFileName(name)))
    } else {
      resp.header("Content-Disposition", encodedFileName(name))
    }

  private def encodedFileName(name: String) = "attachment; filename*=\"" + EncoderUtil.encodeEncodedWord(name, Usage.TEXT_TOKEN) + "\""

  private def queryParam(httpRequest: HttpServerRequest, parameterName: String): Option[String] =
    queryParam(parameterName, httpRequest.uri)

  private def queryParam(parameterName: String, uri: String): Option[String] =
    Option(new QueryStringDecoder(uri).parameters.get(parameterName))
      .toList
      .flatMap(_.asScala)
      .headOption

  private def respondDetails(httpServerResponse: HttpServerResponse, details: ProblemDetails): SMono[Unit] =
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
