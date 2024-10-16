package com.linagora.tmail.james.jmap

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT
import eu.timepit.refined
import org.apache.commons.configuration2.Configuration
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.util.Size

import scala.util.{Failure, Success, Try}

object JMAPExtensionConfiguration {
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY: String = "public.asset.total.size"
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT: PublicAssetTotalSizeLimit = PublicAssetTotalSizeLimit.of(Size.of(20L, Size.Unit.M)).get

  val SUPPORT_MAIL_ADDRESS_PROPERTY: String = "support.mail.address"

  def from(configuration: Configuration): JMAPExtensionConfiguration = {
    val supportMailAddressOpt: Option[MailAddress] =
      Option(configuration.getString(SUPPORT_MAIL_ADDRESS_PROPERTY, null))
        .map(s => new MailAddress(s))

    JMAPExtensionConfiguration(
      publicAssetTotalSizeLimit = Option(configuration.getString(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY, null))
        .map(Size.parse)
        .map(PublicAssetTotalSizeLimit.of(_).get)
        .getOrElse(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT),
      supportMailAddressOpt
    )
  }
}

object PublicAssetTotalSizeLimit {
  def of(size: Size): Try[PublicAssetTotalSizeLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalSizeLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class JMAPExtensionConfiguration(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT,
                                      supportMailAddress: Option[MailAddress] = Option.empty) {
  def this(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit) = {
    this(publicAssetTotalSizeLimit, Option.empty)
  }

  def this(supportMailAddress: MailAddress) = {
    this(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, Option(supportMailAddress))
  }

  def this() = {
    this(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, Option.empty)
  }
}

case class PublicAssetTotalSizeLimit(value: UnsignedInt) {
  def asLong(): Long = value.value
}
