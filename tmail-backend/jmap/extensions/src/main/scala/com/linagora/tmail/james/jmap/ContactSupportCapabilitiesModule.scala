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

package com.linagora.tmail.james.jmap

import java.net.URL

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_CONTACT_SUPPORT
import jakarta.inject.Inject
import org.apache.james.core.{MailAddress, Username}
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

object ContactSupportProperties {
  private val JSON_SUPPORT_MAIL_ADDRESS_PROPERTY: String = "supportMailAddress"
  private val JSON_SUPPORT_HTTP_LINK_PROPERTY: String = "httpLink"
}

case class ContactSupportProperties(mailAddress: Option[MailAddress] = None,
                                    supportLink: Option[URL] = None) extends CapabilityProperties {

  import ContactSupportProperties._

  override def jsonify(): JsObject =
    (mailAddress, supportLink) match {
      case (None, None) => Json.obj()
      case (Some(mailAddress: MailAddress), None) => Json.obj(JSON_SUPPORT_MAIL_ADDRESS_PROPERTY -> mailAddress.asString())
      case (None, Some(supportLink: URL)) => Json.obj(JSON_SUPPORT_HTTP_LINK_PROPERTY -> supportLink.toString)
      case (Some(mailAddress: MailAddress), Some(supportLink: URL)) => Json.obj(
        JSON_SUPPORT_MAIL_ADDRESS_PROPERTY -> mailAddress.asString(),
        JSON_SUPPORT_HTTP_LINK_PROPERTY -> supportLink.toString)
    }
}

case class ContactSupportCapability(contactSupportProperties: ContactSupportProperties) extends Capability {
  val properties: CapabilityProperties = contactSupportProperties
  val identifier: CapabilityIdentifier = LINAGORA_CONTACT_SUPPORT
}

case class ContactSupportCapabilityFactory @Inject()(jmapConfig: JMAPExtensionConfiguration) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    ContactSupportCapability(ContactSupportProperties(jmapConfig.supportMailAddress, jmapConfig.supportHttpLink))

  override def id(): CapabilityIdentifier = LINAGORA_CONTACT_SUPPORT
}

class ContactSupportCapabilitiesModule() extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[ContactSupportCapabilityFactory])
  }
}
