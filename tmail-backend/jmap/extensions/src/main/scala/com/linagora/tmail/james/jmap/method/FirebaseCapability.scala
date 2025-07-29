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
import com.google.inject.multibindings.ProvidesIntoSet
import com.linagora.tmail.james.jmap.firebase.FirebaseConfiguration
import com.linagora.tmail.james.jmap.json.FirebaseSubscriptionSerializer
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_FIREBASE
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.JsObject

import scala.jdk.javaapi.OptionConverters

case class FirebaseCapabilityProperties(apiKey: ApiKey,
                                        appId: AppId,
                                        messagingSenderId: MessagingSenderId,
                                        projectId: ProjectId,
                                        databaseUrl: DatabaseUrl,
                                        storageBucket: StorageBucket,
                                        authDomain: AuthDomain,
                                        vapidPublicKey: VapidPublicKey) extends CapabilityProperties {
  override def jsonify(): JsObject = FirebaseSubscriptionSerializer.firebaseCapabilityWrites.writes(this)
}

case class FirebaseCapability(properties: FirebaseCapabilityProperties) extends Capability {
  val identifier: CapabilityIdentifier = LINAGORA_FIREBASE
}

case class FirebaseCapabilityFactory(configuration: FirebaseConfiguration) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = FirebaseCapability(FirebaseCapabilityProperties(
    ApiKey(OptionConverters.toScala(configuration.apiKey())),
    AppId(OptionConverters.toScala(configuration.appId())),
    MessagingSenderId(OptionConverters.toScala(configuration.messagingSenderId())),
    ProjectId(OptionConverters.toScala(configuration.projectId())),
    DatabaseUrl(OptionConverters.toScala(configuration.databaseUrl())),
    StorageBucket(OptionConverters.toScala(configuration.storageBucket())),
    AuthDomain(OptionConverters.toScala(configuration.authDomain())),
    VapidPublicKey(OptionConverters.toScala(configuration.vapidPublicKey()))))

  override def id(): CapabilityIdentifier = LINAGORA_FIREBASE
}

case class ApiKey(value: Option[String])
case class AppId(value: Option[String])
case class MessagingSenderId(value: Option[String])
case class ProjectId(value: Option[String])
case class DatabaseUrl(value: Option[String])
case class StorageBucket(value: Option[String])
case class AuthDomain(value: Option[String])
case class VapidPublicKey(value: Option[String])

class FirebaseCapabilitiesModule extends AbstractModule {
  @ProvidesIntoSet
  private def capability(configuration: FirebaseConfiguration): CapabilityFactory = FirebaseCapabilityFactory(configuration)
}
