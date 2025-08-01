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

package com.linagora.tmail.james.jmap.saas

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_SAAS
import com.linagora.tmail.saas.api.SaaSAccountRepository
import com.linagora.tmail.saas.model.SaaSPlan
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}
import reactor.core.scala.publisher.SMono

case class SaaSCapabilityProperties(saasPlan: SaaSPlan) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("saasPlan" -> saasPlan.value())
}

final case class SaaSCapability(properties: SaaSCapabilityProperties,
                                identifier: CapabilityIdentifier = LINAGORA_SAAS) extends Capability

class SaaSCapabilityFactory @Inject()(val saaSUserRepository: SaaSAccountRepository) extends CapabilityFactory {
  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    this.createReactive(urlPrefixes, username).block()

  override def createReactive(urlPrefixes: UrlPrefixes, username: Username): SMono[Capability] =
    SMono(saaSUserRepository.getSaaSAccount(username))
      .map(saaSAccount => SaaSCapability(SaaSCapabilityProperties(saaSAccount.saaSPlan())))

  override def id(): CapabilityIdentifier = LINAGORA_SAAS
}

class SaaSCapabilitiesModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[SaaSCapabilityFactory])
  }
}
