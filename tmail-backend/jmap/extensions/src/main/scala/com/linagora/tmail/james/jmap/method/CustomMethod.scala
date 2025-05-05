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

package com.linagora.tmail.james.jmap.method

import com.google.inject.AbstractModule
import com.google.inject.multibindings.{Multibinder, ProvidesIntoSet}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_ECHO
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.CapabilityIdentifier.{CapabilityIdentifier, JMAP_CORE}
import org.apache.james.jmap.core.Invocation.MethodName
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import org.apache.james.jmap.method.{InvocationWithContext, Method}
import org.apache.james.mailbox.MailboxSession
import org.reactivestreams.Publisher
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

object CapabilityIdentifier {
  val LINAGORA_ECHO: CapabilityIdentifier = "com:linagora:params:jmap:echo"
  val LINAGORA_FILTER: CapabilityIdentifier = "com:linagora:params:jmap:filter"
  val LINAGORA_PGP: CapabilityIdentifier = "com:linagora:params:jmap:pgp"
  val LINAGORA_CONTACT: CapabilityIdentifier = "com:linagora:params:jmap:contact:autocomplete"
  val LINAGORA_FORWARD: CapabilityIdentifier = "com:linagora:params:jmap:forward"
  val LINAGORA_FIREBASE: CapabilityIdentifier = "com:linagora:params:jmap:firebase:push"
  val LINAGORA_CALENDAR: CapabilityIdentifier = "com:linagora:params:calendar:event"
  val LINAGORA_TEAM_MAILBOXES: CapabilityIdentifier = "com:linagora:params:jmap:team:mailboxes"
  val LINAGORA_MESSAGE_VAULT: CapabilityIdentifier = "com:linagora:params:jmap:messages:vault"
  val LINAGORA_LABEL: CapabilityIdentifier = "com:linagora:params:jmap:labels"
  val LINAGORA_SETTINGS: CapabilityIdentifier = "com:linagora:params:jmap:settings"
  val LINAGORA_PUBLIC_ASSETS: CapabilityIdentifier = "com:linagora:params:jmap:public:assets"
  val LINAGORA_CONTACT_SUPPORT: CapabilityIdentifier = "com:linagora:params:jmap:contact:support"
  val LINAGORA_DOWNLOAD_ALL: CapabilityIdentifier = "com:linagora:params:downloadAll"
  val LINAGORA_MAILBOX_CLEAR: CapabilityIdentifier = "com:linagora:params:jmap:mailbox:clear"
}

case object CustomCapabilityProperties extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj()
}

case object CustomCapability extends Capability {
  val properties: CapabilityProperties = CustomCapabilityProperties
  val identifier: CapabilityIdentifier = LINAGORA_ECHO
}

class CustomCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(): CapabilityFactory = CustomCapabilityFactory
}

case object CustomCapabilityFactory extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes): Capability = CustomCapability

  override def id(): CapabilityIdentifier = LINAGORA_ECHO
}

class CustomMethodModule extends AbstractModule {
  override def configure(): Unit = {
    install(new CustomCapabilitiesModule())
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[CustomMethod])
  }
}

class CustomMethod extends Method {
  override val methodName: MethodName = MethodName("Linagora/echo")

  override def process(capabilities: Set[CapabilityIdentifier], invocation: InvocationWithContext, mailboxSession: MailboxSession): Publisher[InvocationWithContext] = SMono.just(invocation)

  override val requiredCapabilities: Set[CapabilityIdentifier] = Set(JMAP_CORE, LINAGORA_ECHO)
}
