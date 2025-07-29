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

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import com.linagora.tmail.james.jmap.{JMAPExtensionConfiguration, PublicAssetTotalSizeLimit}
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case class PublicAssetsCapabilityProperties(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("publicAssetTotalSize" -> publicAssetTotalSizeLimit.value.value)
}

final case class PublicAssetsCapability(properties: PublicAssetsCapabilityProperties,
                                        identifier: CapabilityIdentifier = LINAGORA_PUBLIC_ASSETS) extends Capability

class PublicAssetsCapabilityFactory @Inject()(val configuration: JMAPExtensionConfiguration) extends CapabilityFactory {

  override def id(): CapabilityIdentifier = LINAGORA_PUBLIC_ASSETS

  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability = {
    PublicAssetsCapability(PublicAssetsCapabilityProperties(configuration.publicAssetTotalSizeLimit))
  }
}
