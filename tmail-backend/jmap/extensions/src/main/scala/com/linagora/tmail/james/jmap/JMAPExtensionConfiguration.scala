package com.linagora.tmail.james.jmap

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT
import eu.timepit.refined
import org.apache.commons.configuration2.Configuration
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.util.Size

import scala.util.{Failure, Success, Try}

object JMAPExtensionConfiguration {
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY: String = "public.asset.total.size"
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT: PublicAssetTotalSizeLimit = PublicAssetTotalSizeLimit.of(Size.of(20L, Size.Unit.M)).get

  def from(configuration: Configuration): JMAPExtensionConfiguration =
    JMAPExtensionConfiguration(
      publicAssetTotalSizeLimit = Option(configuration.getString(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY, null))
        .map(Size.parse)
        .map(PublicAssetTotalSizeLimit.of(_).get)
        .getOrElse(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT)
    )
}

object PublicAssetTotalSizeLimit {
  def of(size: Size): Try[PublicAssetTotalSizeLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalSizeLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class JMAPExtensionConfiguration(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT) {
}

case class PublicAssetTotalSizeLimit(value: UnsignedInt) {
  def asLong(): Long = value.value
}
