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
import play.api.libs.json.{JsObject, Json}

class ListEmailsTool extends AgentTool {

  override def name: AgentToolName = AgentToolName("list_emails")

  override def definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("List the user's emails in a mailbox as seen by the user, most recent first. " +
      "Returns id, sender, subject, date and a short preview for each email. " +
      "This is cheaper than search_emails: favour this tool and try it before searching. " +
      "Only fall back to search_emails when listing did not help to find the emails."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "mailboxId" -> Json.obj(
          "type" -> "string",
          "description" -> "Id of the mailbox to list emails from. Defaults to the Inbox."),
        "page" -> Json.obj(
          "type" -> "integer",
          "description" -> "Page number to retrieve, starting at 1, default 1"),
        "limit" -> Json.obj(
          "type" -> "integer",
          "description" -> "Maximum number of results per page, default 10, max 20")),
      "required" -> Json.arr()))
}
