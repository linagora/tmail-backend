package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import com.linagora.tmail.james.jmap.{JMAPExtensionConfiguration, PublicAssetTotalSizeLimit}
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

  override def create(urlPrefixes: UrlPrefixes): Capability = {
    PublicAssetsCapability(PublicAssetsCapabilityProperties(configuration.publicAssetTotalSizeLimit))
  }
}
