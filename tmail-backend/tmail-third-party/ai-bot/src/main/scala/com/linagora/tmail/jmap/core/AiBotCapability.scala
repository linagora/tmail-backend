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
package com.linagora.tmail.jmap.core

import com.linagora.tmail.jmap.core.CapabilityIdentifier.LINAGORA_AIBOT
import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}

import eu.timepit.refined.auto._

import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.CapabilityIdentifier.JMAP_CORE
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.method.{InvocationWithContext, Method}
import org.apache.james.mailbox.MailboxSession
import org.reactivestreams.Publisher

import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

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
      .to(classOf[AiBotMethod])
  }
}

class AiBotMethod extends Method {

  override val methodName: MethodName = MethodName("AiBot/Suggest")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = SMono.just(invocation)

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_AIBOT)

}
