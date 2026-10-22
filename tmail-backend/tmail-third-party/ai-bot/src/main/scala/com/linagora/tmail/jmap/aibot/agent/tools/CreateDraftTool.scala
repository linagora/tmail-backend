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

import java.nio.charset.StandardCharsets
import java.util.Date

import com.linagora.tmail.jmap.aibot.agent.{AgentTool, AgentToolName, InvalidToolCallException}
import jakarta.inject.Inject
import jakarta.mail.Flags
import org.apache.james.mailbox.MessageManager.AppendCommand
import org.apache.james.mailbox.exception.MailboxNotFoundException
import org.apache.james.mailbox.model.{MailboxPath, MessageId}
import org.apache.james.mailbox.{DefaultMailboxes, MailboxManager, MailboxSession, MessageManager}
import org.apache.james.mime4j.dom.address.Mailbox
import org.apache.james.mime4j.dom.{Message, Multipart}
import org.apache.james.mime4j.field.address.DefaultAddressParser
import org.apache.james.mime4j.message.{BodyPartBuilder, MultipartBuilder}
import org.apache.james.mime4j.stream.RawField
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._
import scala.util.Try

object CreateDraftTool {
  val TO: ArgumentName = ArgumentName("to")
  val CC: ArgumentName = ArgumentName("cc")
  val SUBJECT: ArgumentName = ArgumentName("subject")
  val BODY: ArgumentName = ArgumentName("body")
  val IN_REPLY_TO: ArgumentName = ArgumentName("inReplyTo")

  private val markdownParser: Parser = Parser.builder().build()

  private val htmlRenderer: HtmlRenderer = HtmlRenderer.builder()
    .escapeHtml(true)
    .sanitizeUrls(true)
    .softbreak("<br />\n")
    .build()

  def body(markdown: String): Multipart =
    MultipartBuilder.create("alternative")
      .addBodyPart(part(markdown, "plain"))
      .addBodyPart(part(htmlRenderer.render(markdownParser.parse(markdown)), "html"))
      .build()

  private def part(text: String, subtype: String): BodyPartBuilder =
    BodyPartBuilder.create()
      .setBody(text, subtype, StandardCharsets.UTF_8)
      .setContentTransferEncoding("quoted-printable")
}

case class DraftRequest(to: Option[Seq[Mailbox]],
                        cc: Option[Seq[Mailbox]],
                        subject: Option[String],
                        body: String,
                        inReplyTo: Option[MessageId])

class CreateDraftTool @Inject()(val mailboxManager: MailboxManager,
                                val messageIdFactory: MessageId.Factory,
                                val emailReader: ToolEmailReader) extends AgentTool {

  override val name: AgentToolName = AgentToolName("create_draft")

  override val definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("Create an email draft in the user's **Drafts** folder. " +
      "The draft is not sent: it returns the **id** of the created draft, so the assistant can open a composer with it " +
      "and ask the user for a sending confirmation.\n\n" +
      "Use `inReplyTo` to draft a reply to an existing email."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        CreateDraftTool.TO.value -> Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string"),
          "description" -> "Recipient email addresses"),
        CreateDraftTool.CC.value -> Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string"),
          "description" -> "CC email addresses"),
        CreateDraftTool.SUBJECT.value -> Json.obj(
          "type" -> "string",
          "description" -> "Email subject"),
        CreateDraftTool.BODY.value -> Json.obj(
          "type" -> "string",
          "description" -> ("Body of the email. **Markdown** is supported for basic styling: " +
            "**bold**, *italic*, lists, [links](https://example.com), `code`...")),
        CreateDraftTool.IN_REPLY_TO.value -> Json.obj(
          "type" -> "string",
          "description" -> "Id of the email being replied to, as returned by search_emails")),
      "required" -> Json.arr(CreateDraftTool.BODY.value)))

  override def execute(session: MailboxSession, arguments: JsObject): SMono[JsValue] =
    SMono.fromCallable(() => parse(ToolArguments(arguments)))
      .flatMap(request => originalEmail(session, request.inReplyTo)
        .map(original => message(session, request, original)))
      .flatMap(message => draftsMailbox(session)
        .flatMap(drafts => SMono(drafts.appendMessageReactive(
          AppendCommand.builder()
            .withFlags(draftFlags)
            .notRecent()
            .build(message),
          session)))
        .map(result => Json.obj(
          "id" -> result.getId.getMessageId.serialize(),
          "mailboxId" -> result.getId.getMailboxId.serialize(),
          "subject" -> message.getSubject)))

  private def draftFlags: Flags = {
    val flags = new Flags(Flags.Flag.DRAFT)
    flags.add(Flags.Flag.SEEN)
    flags
  }

  private def parse(args: ToolArguments): DraftRequest =
    DraftRequest(
      to = addresses(args, CreateDraftTool.TO),
      cc = addresses(args, CreateDraftTool.CC),
      subject = args.string(CreateDraftTool.SUBJECT),
      body = args.requiredString(CreateDraftTool.BODY),
      inReplyTo = args.string(CreateDraftTool.IN_REPLY_TO).map(ToolIds.messageId(messageIdFactory, _)))

  private def addresses(args: ToolArguments, name: ArgumentName): Option[Seq[Mailbox]] =
    Option(args.strings(name).map(address)).filter(_.nonEmpty)

  private def address(value: String): Mailbox =
    Try(DefaultAddressParser.DEFAULT.parseMailbox(value))
      .toOption
      .filter(mailbox => Option(mailbox.getDomain).exists(_.nonEmpty))
      .getOrElse(throw InvalidToolCallException(s"Invalid email address: $value"))

  private def originalEmail(session: MailboxSession, inReplyTo: Option[MessageId]): SMono[Option[ToolEmail]] =
    inReplyTo.map(id => emailReader.readOne(session, id).map(Option(_)))
      .getOrElse(SMono.just(None))

  private def message(session: MailboxSession, request: DraftRequest, original: Option[ToolEmail]): Message = {
    val builder = Message.Builder.of()
    builder.setFrom(session.getUser.asString())
    request.to.map(_.asJava).foreach(builder.setTo)
    request.cc.map(_.asJava).foreach(builder.setCc)
    builder.setSubject(subject(request, original))
    builder.setDate(new Date())
    builder.generateMessageId(session.getUser.getDomainPart.map(_.asString()).orElse("localhost"))
    original.flatMap(_.messageIdHeader).foreach(messageId => {
      builder.setField(new RawField("In-Reply-To", messageId))
      val references = original.flatMap(_.references).map(_ + " " + messageId).getOrElse(messageId)
      builder.setField(new RawField("References", references))
    })
    builder.setBody(CreateDraftTool.body(request.body))
    builder.build()
  }

  private def subject(request: DraftRequest, original: Option[ToolEmail]): String =
    request.subject
      .orElse(original.flatMap(_.subject).map(subject =>
        if (subject.toLowerCase.startsWith("re:")) subject else s"Re: $subject"))
      .getOrElse("")

  private def draftsMailbox(session: MailboxSession): SMono[MessageManager] = {
    val path = MailboxPath.forUser(session.getUser, DefaultMailboxes.DRAFTS)
    SMono(mailboxManager.getMailboxReactive(path, session))
      .onErrorResume {
        case _: MailboxNotFoundException => SMono(mailboxManager.createMailboxReactive(path, session))
          .flatMap(_ => SMono(mailboxManager.getMailboxReactive(path, session)))
        case e => SMono.error(e)
      }
  }
}
