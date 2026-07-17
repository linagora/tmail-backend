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
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.event.ApplyWhenFilter
import com.linagora.tmail.jmap.aibot.CapabilityIdentifier.LINAGORA_AIBOT
import eu.timepit.refined.auto._
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, URL, UrlPrefixes}
import org.apache.james.jmap.method.Method
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

object CapabilityIdentifier {
  val LINAGORA_AIBOT: CapabilityIdentifier = "com:linagora:params:jmap:aibot"
}

case class AiBotCapabilityProperties(scribeEndpoint: URL) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("scribeEndpoint" -> scribeEndpoint.value)
}

case class AiBotCapability(properties: AiBotCapabilityProperties,
                           identifier: CapabilityIdentifier = LINAGORA_AIBOT) extends Capability

class AiBotCapabilitiesModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[AiBotCapabilityFactory])
  }
}

class AiBotCapabilityFactory @Inject()(val applyWhenFilter: ApplyWhenFilter) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    this.createReactive(urlPrefixes, username).block()

  override def createReactive(urlPrefixes: UrlPrefixes, username: Username): SMono[Capability] = {
    SMono(applyWhenFilter.isEligible(username))
      .filter(_.booleanValue())
      .map(_ => AiBotCapability(AiBotCapabilityProperties(URL(urlPrefixes.httpUrlPrefix.toString + "/ai/v1/chat/completions"))))
  }

  override def id(): CapabilityIdentifier = LINAGORA_AIBOT
}

class AiMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new AiBotCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[AiSuggestionMethod])

    Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
      .addBinding()
      .to(classOf[AIChatCompletionRoutes])
  }
}