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
import play.api.libs.json.{JsObject, Json}

import scala.jdk.CollectionConverters.CollectionHasAsScala

case class AgentToolName(nameAsString: String)

trait AgentTool {
  def name: AgentToolName
  def definition: JsObject
}

class AgentToolRegistry @Inject()(val tools: java.util.Set[AgentTool]) {

  private val internalMap: Map[AgentToolName, AgentTool] = tools.asScala.map(tool => tool.name -> tool)
    .toMap

  def get(name: AgentToolName): Option[AgentTool] = internalMap.get(name)

  def definitions(): Seq[JsObject] = internalMap.values.toSeq
    .sortBy(_.name.nameAsString)
    .map(tool => Json.obj("type" -> "function", "function" -> tool.definition))
}
