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

class CreateDraftTool extends AgentTool {

  override def name: AgentToolName = AgentToolName("create_draft")

  override def definition: JsObject = Json.obj(
    "name" -> name.nameAsString,
    "description" -> ("Create an email draft in the user's Drafts folder. " +
      "The draft is not sent; the user will review and send it. " +
      "Use inReplyTo to draft a reply to an existing email."),
    "parameters" -> Json.obj(
      "type" -> "object",
      "properties" -> Json.obj(
        "to" -> Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string"),
          "description" -> "Recipient email addresses"),
        "cc" -> Json.obj(
          "type" -> "array",
          "items" -> Json.obj("type" -> "string"),
          "description" -> "CC email addresses"),
        "subject" -> Json.obj(
          "type" -> "string",
          "description" -> "Email subject"),
        "body" -> Json.obj(
          "type" -> "string",
          "description" -> "Plain text body of the email"),
        "inReplyTo" -> Json.obj(
          "type" -> "string",
          "description" -> "Id of the email being replied to, as returned by search_emails")),
      "required" -> Json.arr("body")))
}
