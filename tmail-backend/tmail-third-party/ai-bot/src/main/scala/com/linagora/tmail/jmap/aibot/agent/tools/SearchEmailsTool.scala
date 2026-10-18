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

class SearchEmailsTool extends AgentTool {

  override def name: AgentToolName = AgentToolName("search_emails")

  override def definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("Search the user's emails with filters. All filters are optional and combined with AND. " +
      "Returns matching emails, most recent first, with id, sender, subject, date and a short preview. " +
      "This is more expensive than list_emails: prefer list_emails first and use this tool only when filtering is needed. " +
      "Use the returned id with create_draft to reply to an email."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "from" -> Json.obj(
          "type" -> "string",
          "description" -> "Sender email address or name"),
        "to" -> Json.obj(
          "type" -> "string",
          "description" -> "Recipient email address or name"),
        "text" -> Json.obj(
          "type" -> "string",
          "description" -> "Text to search in the subject, addresses and body"),
        "after" -> Json.obj(
          "type" -> "string",
          "description" -> "Only emails received after this date (ISO 8601, e.g. 2026-10-14)"),
        "before" -> Json.obj(
          "type" -> "string",
          "description" -> "Only emails received before this date (ISO 8601, e.g. 2026-10-14)"),
        "inMailboxes" -> Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string"),
          "description" -> "Only emails in one of these mailbox ids. Searches all mailboxes if omitted"),
        "unread" -> Json.obj(
          "type" -> "boolean",
          "description" -> "Only return unread emails"),
        "limit" -> Json.obj(
          "type" -> "integer",
          "description" -> "Maximum number of results, default 10, max 20")),
      "required" -> Json.arr()))
}
