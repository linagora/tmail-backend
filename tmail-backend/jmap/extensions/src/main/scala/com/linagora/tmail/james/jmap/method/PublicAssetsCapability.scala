package com.linagora.tmail.james.jmap.method

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Inject}
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import org.apache.commons.configuration2.Configuration
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UnsignedInt, UrlPrefixes}
import play.api.libs.json.{JsObject, Json}

import jakarta.inject.Named

case class PublicAssetTotalByteLimit(value: UnsignedInt)

case class PublicAssetsCapabilityProperties(publicAssetTotalByteSize: PublicAssetTotalByteLimit) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("publicAssetTotalSize" -> publicAssetTotalByteSize.value.value)
}

final case class PublicAssetsCapability(properties: PublicAssetsCapabilityProperties,
                                        identifier: CapabilityIdentifier = LINAGORA_PUBLIC_ASSETS) extends Capability
object PublicAssetsConfiguration {
  val PUBLIC_ASSET_TOTAL_BYTE_SIZE_PROPERTY: String = "publicAssetTotalSize"
  val PUBLIC_ASSET_TOTAL_BYTE_SIZE_DEFAULT: Int = 20 * 1024 * 1024 // 20MB
}

class PublicAssetsCapabilityFactory @Inject()(@Named("jmap") jmapConfiguration: Configuration) extends CapabilityFactory {

  import PublicAssetsConfiguration._

  override def id(): CapabilityIdentifier = LINAGORA_PUBLIC_ASSETS

  override def create(urlPrefixes: UrlPrefixes): Capability = {
    val publicAssetTotalByteLimit = jmapConfiguration.getInteger(PUBLIC_ASSET_TOTAL_BYTE_SIZE_PROPERTY, PUBLIC_ASSET_TOTAL_BYTE_SIZE_DEFAULT)
    PublicAssetsCapability(PublicAssetsCapabilityProperties(PublicAssetTotalByteLimit(UnsignedInt.liftOrThrow(publicAssetTotalByteLimit.longValue()))))
  }
}

class PublicAssetsModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[PublicAssetsCapabilityFactory])
  }
}