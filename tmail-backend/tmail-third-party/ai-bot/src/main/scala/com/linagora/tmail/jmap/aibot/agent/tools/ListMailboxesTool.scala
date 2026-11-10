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
import org.apache.james.mailbox.model.MailboxMetaData
import org.apache.james.mailbox.model.search.MailboxQuery
import org.apache.james.mailbox.{MailboxManager, MailboxSession, Role}
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._

class ListMailboxesTool @Inject()(val mailboxManager: MailboxManager) extends AgentTool {

  override val name: AgentToolName = AgentToolName("list_mailboxes")
  override val confirm: Boolean = false

  override val definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("List the user's mailboxes (folders): id, name, role (inbox, sent, drafts, trash, spam, " +
      "archive... or none for a folder of the user), number of emails and of unread emails. " +
      "Use it to find the id of a mailbox for list_emails or search_emails, when the user names a folder, " +
      "or to tell how many emails or unread emails a folder holds."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(),
      "required" -> Json.arr()))

  override def execute(session: MailboxSession, arguments: JsObject): SMono[JsValue] =
    SFlux(mailboxManager.search(MailboxQuery.privateMailboxesBuilder(session).build(), session))
      .map(mailbox => json(session, mailbox))
      .collectSeq()
      .map(mailboxes => Json.obj("mailboxes" -> mailboxes.sortBy(mailbox => (mailbox \ "name").as[String])))

  private def json(session: MailboxSession, mailbox: MailboxMetaData): JsObject = {
    val role: Option[Role] = Role.from(mailbox.getPath.getName).toScala
      .filter(_ => mailbox.getPath.belongsTo(session))
    Json.obj(
      "id" -> mailbox.getId.serialize(),
      "name" -> mailbox.getPath.getName,
      "role" -> role.map(_.serialize()),
      "totalEmails" -> mailbox.getCounters.getCount,
      "unreadEmails" -> mailbox.getCounters.getUnseen)
  }
}
