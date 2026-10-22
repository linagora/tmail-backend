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

import com.linagora.tmail.jmap.aibot.agent.{AgentTool, AgentToolName}
import jakarta.inject.Inject
import org.apache.james.mailbox.MailboxSession
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.SMono

object GetEmailDetailsTool {
  val BODY_MAX_LENGTH = 5000
}

class GetEmailDetailsTool @Inject()(val emailReader: ToolEmailReader,
                                    val messageIdFactory: MessageId.Factory) extends AgentTool {

  override val name: AgentToolName = AgentToolName("get_email_details")

  override val definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("Get the details of a single email: sender, recipients, cc, subject, date, " +
      "mailboxes, unread status, attachment names and the text body (truncated if too long). " +
      "Use it after list_emails or search_emails returned an id and you need to read the email, " +
      "for example to summarize it or to draft a reply with create_draft."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "id" -> Json.obj(
          "type" -> "string",
          "description" -> "Id of the email to retrieve, as returned by list_emails or search_emails")),
      "required" -> Json.arr("id")))

  override def execute(session: MailboxSession, arguments: JsObject): SMono[JsValue] =
    SMono.fromCallable(() => ToolIds.messageId(messageIdFactory, ToolArguments(arguments).requiredString("id")))
      .flatMap(id => emailReader.readOne(session, id))
      .map(email => {
        val body = email.textBody.getOrElse("")
        Json.obj(
          "id" -> email.id.serialize(),
          "from" -> email.from,
          "to" -> email.to,
          "cc" -> email.cc,
          "subject" -> email.subject,
          "receivedAt" -> ToolEmail.isoDate(email.receivedAt),
          "mailboxIds" -> email.mailboxIds.map(_.serialize()),
          "unread" -> email.isUnread,
          "attachments" -> email.attachmentNames,
          "body" -> ToolEmail.truncate(body, GetEmailDetailsTool.BODY_MAX_LENGTH),
          "truncated" -> (body.length > GetEmailDetailsTool.BODY_MAX_LENGTH))
      })
}