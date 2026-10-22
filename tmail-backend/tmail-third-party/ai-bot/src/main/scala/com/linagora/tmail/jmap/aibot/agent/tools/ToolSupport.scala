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
package com.linagora.tmail.jmap.aibot.agent.tools

import java.io.InputStream
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, OffsetDateTime, ZoneOffset}
import java.util.Date

import com.linagora.tmail.jmap.aibot.agent.InvalidToolCallException
import jakarta.inject.Inject
import jakarta.mail.Flags
import org.apache.james.jmap.api.projections.{MessageFastViewPrecomputedProperties, MessageFastViewProjection}
import org.apache.james.mailbox.model.{FetchGroup, MailboxId, MessageId, MessageResult}
import org.apache.james.mailbox.{MailboxSession, MessageIdManager}
import org.apache.james.mime4j.dom.address.{AddressList, Mailbox, MailboxList}
import org.apache.james.mime4j.dom.{Entity, Message, Multipart}
import org.apache.james.mime4j.message.DefaultMessageBuilder
import org.apache.james.mime4j.stream.MimeConfig
import org.apache.james.util.ReactorUtils
import org.apache.james.util.html.HtmlTextExtractor
import org.apache.james.util.mime.MessageContentExtractor
import org.slf4j.{Logger, LoggerFactory}
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import reactor.core.scala.publisher.{SFlux, SMono}
import reactor.core.scheduler.Schedulers

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Try

case class EmailNotFoundException(id: String) extends RuntimeException(s"Email $id not found")

case class ToolArguments(json: JsObject) {

  private def value(name: String): Option[JsValue] = json.value.get(name).filter {
    case JsNull => false
    case JsString(string) => string.trim.nonEmpty
    case _ => true
  }

  def string(name: String): Option[String] = value(name).map {
    case JsString(string) => string.trim
    case _ => throw InvalidToolCallException(s"'$name' must be a string")
  }

  def requiredString(name: String): String =
    string(name).getOrElse(throw InvalidToolCallException(s"Missing '$name'"))

  def strings(name: String): Seq[String] = value(name) match {
    case None => Seq()
    case Some(JsString(string)) => Seq(string.trim)
    case Some(JsArray(values)) => values.toSeq.map {
      case JsString(string) => string.trim
      case _ => throw InvalidToolCallException(s"'$name' must be an array of strings")
    }.filter(_.nonEmpty)
    case Some(_) => throw InvalidToolCallException(s"'$name' must be an array of strings")
  }

  def int(name: String): Option[Int] = value(name).map {
    case JsNumber(number) if number.isValidInt => number.toInt
    case JsString(string) => string.trim.toIntOption
      .getOrElse(throw InvalidToolCallException(s"'$name' must be an integer"))
    case _ => throw InvalidToolCallException(s"'$name' must be an integer")
  }

  def boolean(name: String): Option[Boolean] = value(name).map {
    case JsBoolean(boolean) => boolean
    case JsString(string) => string.trim.toBooleanOption
      .getOrElse(throw InvalidToolCallException(s"'$name' must be a boolean"))
    case _ => throw InvalidToolCallException(s"'$name' must be a boolean")
  }

  def date(name: String): Option[Date] = string(name).map(string =>
    Try(OffsetDateTime.parse(string).toInstant)
      .orElse(Try(Instant.parse(string)))
      .orElse(Try(LocalDate.parse(string).atStartOfDay(ZoneOffset.UTC).toInstant))
      .map(Date.from)
      .getOrElse(throw InvalidToolCallException(s"'$name' must be an ISO 8601 date")))

  def limit(name: String, default: Int, max: Int): Int = int(name) match {
    case None => default
    case Some(limit) if limit < 1 => throw InvalidToolCallException(s"'$name' must be positive")
    case Some(limit) => Math.min(limit, max)
  }
}

object ToolIds {
  def messageId(factory: MessageId.Factory, id: String): MessageId =
    Try(factory.fromString(id))
      .getOrElse(throw InvalidToolCallException(s"Invalid email id: $id"))

  def mailboxId(factory: MailboxId.Factory, id: String): MailboxId =
    Try(factory.fromString(id))
      .getOrElse(throw InvalidToolCallException(s"Invalid mailbox id: $id"))
}

trait ToolEmailHeaders {
  def message: Message

  def subject: Option[String] = Option(message.getSubject)

  def messageIdHeader: Option[String] = Option(message.getMessageId)

  def references: Option[String] = Option(message.getHeader)
    .flatMap(header => Option(header.getField("References")))
    .map(_.getBody.trim)

  def from: Seq[String] = ToolEmail.addresses(message.getFrom)

  def to: Seq[String] = ToolEmail.addresses(message.getTo)

  def cc: Seq[String] = ToolEmail.addresses(message.getCc)
}

case class ToolEmailSummary(id: MessageId,
                            receivedAt: Date,
                            flags: Flags,
                            message: Message,
                            preview: String) extends ToolEmailHeaders {

  def json: JsObject = Json.obj(
    "id" -> id.serialize(),
    "from" -> from.headOption,
    "subject" -> subject,
    "receivedAt" -> ToolEmail.isoDate(receivedAt),
    "unread" -> !flags.contains(Flags.Flag.SEEN),
    "preview" -> preview)
}

case class ToolEmail(id: MessageId,
                     mailboxIds: Seq[MailboxId],
                     receivedAt: Date,
                     flags: Flags,
                     message: Message,
                     textBody: Option[String]) extends ToolEmailHeaders {

  def isUnread: Boolean = !flags.contains(Flags.Flag.SEEN)

  def attachmentNames: Seq[String] = ToolEmail.attachmentNames(message)
}

object ToolEmail {
  def truncate(text: String, length: Int): String =
    if (text.length <= length) text else text.take(length) + "…"

  def isoDate(date: Date): String = DateTimeFormatter.ISO_INSTANT.format(date.toInstant)

  def addresses(list: MailboxList): Seq[String] =
    Option(list).map(_.asScala.toSeq.map(format)).getOrElse(Seq())

  def addresses(list: AddressList): Seq[String] =
    Option(list).map(_.flatten().asScala.toSeq.map(format)).getOrElse(Seq())

  private def format(mailbox: Mailbox): String =
    Option(mailbox.getName).filter(_.nonEmpty)
      .map(name => s"$name <${mailbox.getAddress}>")
      .getOrElse(mailbox.getAddress)

  private def attachmentNames(entity: Entity): Seq[String] = entity.getBody match {
    case multipart: Multipart => multipart.getBodyParts.asScala.toSeq.flatMap(attachmentNames)
    case _ => Option(entity.getFilename).filter(_ => entity.getDispositionType != "inline").toSeq
  }

  def parse(content: => InputStream): SMono[Message] =
    SMono.fromCallable(() => {
      val messageBuilder = new DefaultMessageBuilder()
      messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE)
      messageBuilder.parseMessage(content)
    }).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
}

object ToolEmailReader {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[ToolEmailReader])
  private val LOW_CONCURRENCY = 2
}

class ToolEmailReader @Inject()(val messageIdManager: MessageIdManager,
                                val messageFastViewProjection: MessageFastViewProjection,
                                val fastViewFactory: MessageFastViewPrecomputedProperties.Factory,
                                val htmlTextExtractor: HtmlTextExtractor) {

  def summaries(session: MailboxSession, ids: Seq[MessageId]): SMono[Seq[ToolEmailSummary]] =
    if (ids.isEmpty) {
      SMono.just(Seq())
    } else {
      SMono(messageFastViewProjection.retrieve(ids.asJava))
        .map(_.asScala.toMap)
        .flatMap(fastViews => previews(session, ids.filterNot(fastViews.contains))
          .map(computed => fastViews.view.mapValues(_.getPreview.getValue).toMap ++ computed))
        .flatMap(previews => SFlux(messageIdManager.getMessagesReactive(ids.asJava, FetchGroup.HEADERS, session))
          .collectSeq()
          .flatMap(results => SFlux.fromIterable(group(ids, results))
            .concatMap(messages => summary(messages, previews.getOrElse(messages.head.getMessageId, "")))
            .collectSeq()))
    }

  def read(session: MailboxSession, ids: Seq[MessageId]): SMono[Seq[ToolEmail]] =
    if (ids.isEmpty) {
      SMono.just(Seq())
    } else {
      SFlux(messageIdManager.getMessagesReactive(ids.asJava, FetchGroup.FULL_CONTENT, session))
        .collectSeq()
        .flatMap(results => SFlux.fromIterable(group(ids, results))
          .concatMap(email)
          .collectSeq())
    }

  def readOne(session: MailboxSession, id: MessageId): SMono[ToolEmail] =
    read(session, Seq(id))
      .flatMap(emails => emails.headOption
        .map(SMono.just)
        .getOrElse(SMono.error(EmailNotFoundException(id.serialize()))))

  private def group(ids: Seq[MessageId], results: Seq[MessageResult]): Seq[Seq[MessageResult]] = {
    val byId: Map[MessageId, Seq[MessageResult]] = results.groupBy(_.getMessageId)
    ids.distinct.flatMap(byId.get)
  }

  private def previews(session: MailboxSession, missing: Seq[MessageId]): SMono[Map[MessageId, String]] =
    if (missing.isEmpty) {
      SMono.just(Map())
    } else {
      SFlux(messageIdManager.getMessagesReactive(missing.asJava, FetchGroup.FULL_CONTENT, session))
        .collectSeq()
        .flatMapMany(results => SFlux.fromIterable(group(missing, results).map(_.head)))
        .flatMap(computePreview, ToolEmailReader.LOW_CONCURRENCY)
        .collectSeq()
        .map(_.toMap)
    }

  private def computePreview(result: MessageResult): SMono[(MessageId, String)] =
    SMono.fromCallable(() => fastViewFactory.from(result))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
      .doOnNext(fastView => SMono(messageFastViewProjection.store(result.getMessageId, fastView))
        .doOnError(e => ToolEmailReader.LOGGER.error("Cannot store the fast view projection of {}", result.getMessageId, e))
        .subscribeOn(Schedulers.parallel())
        .subscribe())
      .map(fastView => result.getMessageId -> fastView.getPreview.getValue)

  private def summary(results: Seq[MessageResult], preview: String): SMono[ToolEmailSummary] = {
    val first = results.head
    ToolEmail.parse(first.getHeaders.getInputStream)
      .map(message => ToolEmailSummary(
        id = first.getMessageId,
        receivedAt = first.getInternalDate,
        flags = first.getFlags,
        message = message,
        preview = preview))
  }

  private def email(results: Seq[MessageResult]): SMono[ToolEmail] = {
    val first = results.head
    ToolEmail.parse(first.getFullContent.getInputStream)
      .map(message => {
        val content = new MessageContentExtractor().extract(message)
        ToolEmail(
          id = first.getMessageId,
          mailboxIds = results.map(_.getMailboxId).distinct,
          receivedAt = first.getInternalDate,
          flags = first.getFlags,
          message = message,
          textBody = content.getTextBody.toScala
            .orElse(content.getHtmlBody.toScala.map(htmlTextExtractor.toPlainText)))
      })
  }
}
