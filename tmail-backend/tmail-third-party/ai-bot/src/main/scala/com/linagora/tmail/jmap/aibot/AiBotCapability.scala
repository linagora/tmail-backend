/** ******************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 * ****************************************************************** */
package com.linagora.tmail.jmap.aibot

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.jmap.aibot.CapabilityIdentifier.LINAGORA_AIBOT
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, URL, UrlPrefixes}
import org.apache.james.jmap.method.Method
import play.api.libs.json.{JsObject, Json}

object CapabilityIdentifier {
  val LINAGORA_AIBOT: CapabilityIdentifier = "com:linagora:params:jmap:aibot"
}

case class AiBotCapabilityProperties(scribeEndpoint: URL) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("scribeEndpoint" -> scribeEndpoint.value)
}

case class AiBotCapability(properties: AiBotCapabilityProperties,
                           identifier: CapabilityIdentifier = LINAGORA_AIBOT) extends Capability

class AiBotCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = AiBotCapabilityFactory
}

case object AiBotCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    AiBotCapability(AiBotCapabilityProperties(URL(urlPrefixes.httpUrlPrefix.toString + "/ai/v1/chat/completions")))

  override def id(): CapabilityIdentifier = LINAGORA_AIBOT
}

class AiBotMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new AiBotCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[AiBotSuggestionMethod])

    Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
      .addBinding()
      .to(classOf[JmapAiRoutes])
  }
}