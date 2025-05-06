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
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import org.apache.james.jmap.method.Method
import play.api.libs.json.{JsObject, Json}

object CapabilityIdentifier {
  val LINAGORA_AIBOT: CapabilityIdentifier = "com:linagora:params:jmap:aibot"
}

case object AiBotCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object AiBotCapability extends Capability {
  val properties: CapabilityProperties = AiBotCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_AIBOT
}

class AiBotCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = AiBotCapabilityFactory
}

case object AiBotCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = AiBotCapability

  override def id(): CapabilityIdentifier = LINAGORA_AIBOT
}

class AiBotMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new AiBotCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[AiBotSuggestionMethod])
  }
}