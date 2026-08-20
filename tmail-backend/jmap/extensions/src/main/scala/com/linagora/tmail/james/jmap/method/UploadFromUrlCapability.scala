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

import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_UPLOAD_FROM_URL
import com.linagora.tmail.james.jmap.upload.UploadFromUrlConfiguration
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, URL, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

case class UploadFromUrlCapabilityProperties(uploadUrl: URL,
                                             allowedSources: Option[Seq[String]]) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("uploadUrl" -> uploadUrl.value) ++
    allowedSources.map(sources => Json.obj("allowedSources" -> sources)).getOrElse(Json.obj())
}

case class UploadFromUrlCapability(properties: UploadFromUrlCapabilityProperties,
                                   identifier: CapabilityIdentifier = LINAGORA_UPLOAD_FROM_URL) extends Capability

class UploadFromUrlCapabilityFactory @Inject()(configuration: UploadFromUrlConfiguration) extends CapabilityFactory {
  override def id(): CapabilityIdentifier = LINAGORA_UPLOAD_FROM_URL

  override def create(urlPrefixes: UrlPrefixes, username: Username): Capability =
    UploadFromUrlCapability(UploadFromUrlCapabilityProperties(
      URL(urlPrefixes.httpUrlPrefix.toString + "/upload-from-url/{accountId}"),
      Option.when(configuration.allowedSources.nonEmpty)(configuration.allowedSources.map(_.asString))))
}
