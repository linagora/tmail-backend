package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.method.CapabilityIdentifier.LINAGORA_PUBLIC_ASSETS
import com.linagora.tmail.james.jmap.method.PublicAssetTotalByteLimit.PUBLIC_ASSET_TOTAL_BYTE_LIMIT_DEFAULT
import eu.timepit.refined
import jakarta.inject.Named
import org.apache.commons.configuration2.Configuration
import org.apache.james.jmap.core.CapabilityIdentifier.CapabilityIdentifier
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.jmap.core.{Capability, CapabilityFactory, CapabilityProperties, UnsignedInt, UrlPrefixes}
import org.apache.james.util.Size
import play.api.libs.json.{JsObject, Json}

import scala.util.{Failure, Success, Try}

object PublicAssetTotalByteLimit {
  val PUBLIC_ASSET_TOTAL_BYTE_LIMIT_DEFAULT: PublicAssetTotalByteLimit = PublicAssetTotalByteLimit(UnsignedInt.liftOrThrow(20 * 1024 * 1024)) // 20MB

  def of(size: Size): Try[PublicAssetTotalByteLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalByteLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class PublicAssetTotalByteLimit(value: UnsignedInt)

case class PublicAssetsCapabilityProperties(publicAssetTotalByteSize: PublicAssetTotalByteLimit) extends CapabilityProperties {
  override def jsonify(): JsObject = Json.obj("publicAssetTotalSize" -> publicAssetTotalByteSize.value.value)
}

final case class PublicAssetsCapability(properties: PublicAssetsCapabilityProperties,
                                        identifier: CapabilityIdentifier = LINAGORA_PUBLIC_ASSETS) extends Capability

object PublicAssetsConfiguration {
  val PUBLIC_ASSET_TOTAL_BYTE_SIZE_PROPERTY: String = "publicAssetTotalSize"
}

class PublicAssetsCapabilityFactory @Inject()(@Named("jmap") jmapConfiguration: Configuration) extends CapabilityFactory {

  import PublicAssetsConfiguration._

  override def id(): CapabilityIdentifier = LINAGORA_PUBLIC_ASSETS

  override def create(urlPrefixes: UrlPrefixes): Capability = {
    val publicAssetTotalByteLimit = Option(jmapConfiguration.getString(PUBLIC_ASSET_TOTAL_BYTE_SIZE_PROPERTY, null))
      .map(value => Size.parse(value))
      .map(size => PublicAssetTotalByteLimit.of(size).get)
      .getOrElse(PUBLIC_ASSET_TOTAL_BYTE_LIMIT_DEFAULT)

    PublicAssetsCapability(PublicAssetsCapabilityProperties(publicAssetTotalByteLimit))
  }
}
