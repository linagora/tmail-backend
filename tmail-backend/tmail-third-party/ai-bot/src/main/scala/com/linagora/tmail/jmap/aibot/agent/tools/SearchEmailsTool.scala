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

import com.linagora.tmail.jmap.aibot.agent.AgentTool
import play.api.libs.json.{JsObject, Json}

class SearchEmailsTool extends AgentTool {

  override def name: String = "search_emails"

  override val definition: JsObject = Json.obj(
    "name" -> name,
    "description" -> ("Search the user's emails. " +
      "Returns a list of matching emails with id, sender, subject, date and a short preview. " +
      "Use the returned id with create_draft to reply to an email."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "from" -> Json.obj(
          "type" -> "string",
          "description" -> "Sender email address"),
        "keyword" -> Json.obj(
          "type" -> "string",
          "description" -> "Text to search in subject and body"),
        "unread" -> Json.obj(
          "type" -> "boolean",
          "description" -> "Only return unread emails"),
        "after" -> Json.obj(
          "type" -> "string",
          "description" -> "Only emails received after this date (ISO 8601, e.g. 2026-10-14)"),
        "limit" -> Json.obj(
          "type" -> "integer",
          "description" -> "Maximum number of results, default 10, max 20")),
      "required" -> Json.arr()))
}
