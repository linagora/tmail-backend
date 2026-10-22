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

package com.linagora.tmail.jmap.aibot.agent

import jakarta.inject.Inject
import org.apache.james.mailbox.MailboxSession
import play.api.libs.json.{JsObject, JsValue, Json}
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.util.Try

case class AgentToolName(nameAsString: String)

trait AgentTool {
  def name: AgentToolName
  def definition: JsObject
  def execute(session: MailboxSession, arguments: JsObject): SMono[JsValue]
}

case class ToolCall(id: String, name: AgentToolName, arguments: JsObject)

object ToolCall {
  def parse(json: JsValue): Either[String, ToolCall] =
    for {
      id <- (json \ "id").asOpt[String].toRight("Missing 'id'")
      name <- (json \ "function" \ "name").asOpt[String].toRight("Missing 'function.name'")
      arguments <- parseArguments(json \ "function" \ "arguments")
    } yield ToolCall(id, AgentToolName(name), arguments)


  private def parseArguments(raw: play.api.libs.json.JsLookupResult): Either[String, JsObject] =
    raw.asOpt[JsObject]
      .map(Right(_))
      .orElse(raw.asOpt[String].map(string =>
        Try(Json.parse(string)).toOption
          .flatMap(_.asOpt[JsObject])
          .toRight("'function.arguments' is not a valid JSON object")))
      .getOrElse(Right(Json.obj()))
}

case class ToolResult(toolCallId: String, content: JsValue) {
  def asJson: JsObject = Json.obj(
    "role" -> "tool",
    "tool_call_id" -> toolCallId,
    "content" -> Json.stringify(content))
}
case class InvalidToolCallException(message: String) extends IllegalArgumentException(message)

case class ToolNotFoundException(name: AgentToolName) extends RuntimeException(s"Unknown tool: ${name.nameAsString}")


class AgentToolRegistry @Inject()(val tools: java.util.Set[AgentTool]) {

  private val internalMap: Map[AgentToolName, AgentTool] = tools.asScala.map(tool => tool.name -> tool)
    .toMap

  def get(name: AgentToolName): Option[AgentTool] = internalMap.get(name)

  def definitions(): Seq[JsObject] = internalMap.values.toSeq
    .sortBy(_.name.nameAsString)
    .map(tool => Json.obj("type" -> "function", "function" -> tool.definition))

  def execute(session: MailboxSession, toolCall: ToolCall): SMono[ToolResult] =
    get(toolCall.name) match {
      case Some(tool) => tool.execute(session, toolCall.arguments).map(content => ToolResult(toolCall.id, content))
      case None => SMono.error(ToolNotFoundException(toolCall.name))
    }
}
