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

import com.linagora.tmail.james.jmap.UnauthenticatedBlobAccessConfiguration
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_UNAUTHENTICATED_BLOB_ACCESS
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, URL, UnsignedInt, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case class UnauthenticatedBlobAccessCapabilityProperties(endpoint: URL,
                                                         tokenTtlInSeconds: UnsignedInt) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj(
    "endpoint" -> endpoint.value,
    "tokenTtlInSeconds" -> tokenTtlInSeconds.value)
}

case class UnauthenticatedBlobAccessCapability(properties: UnauthenticatedBlobAccessCapabilityProperties,
                                               identifier: CapabilityIdentifier = LINAGORA_UNAUTHENTICATED_BLOB_ACCESS) extends Capability

class UnauthenticatedBlobAccessCapabilityFactory @Inject()(configuration: UnauthenticatedBlobAccessConfiguration) extends CapabilityFactory {

  override def id(): CapabilityIdentifier = LINAGORA_UNAUTHENTICATED_BLOB_ACCESS

  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    UnauthenticatedBlobAccessCapability(UnauthenticatedBlobAccessCapabilityProperties(
      endpoint = URL(urlPrefixes.httpUrlPrefix.toString + "/unauthenticatedDownload/{accountId}/{blobId}?token={token}"),
      tokenTtlInSeconds = UnsignedInt.liftOrThrow(configuration.tokenTtl.getSeconds)))
}
