package com.linagora.tmail.james.jmap

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.{PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, SUPPORT_MAIL_ADDRESS_DEFAULT}
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
  val SUPPORT_MAIL_ADDRESS_DEFAULT: MailAddress = new MailAddress("NoSupportMailAddress@linagora.com")

  def from(configuration: Configuration): JMAPExtensionConfiguration = {
    val supportMailAddress: MailAddress = new MailAddress(configuration.getString(SUPPORT_MAIL_ADDRESS_PROPERTY, SUPPORT_MAIL_ADDRESS_DEFAULT.asString()))
    JMAPExtensionConfiguration(
      publicAssetTotalSizeLimit = Option(configuration.getString(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY, null))
        .map(Size.parse)
        .map(PublicAssetTotalSizeLimit.of(_).get)
        .getOrElse(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT),
      supportMailAddress
    )
  }
}

object PublicAssetTotalSizeLimit {
  def of(size: Size): Try[PublicAssetTotalSizeLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalSizeLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class JMAPExtensionConfiguration(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT,
                                      supportMailAddress: MailAddress = SUPPORT_MAIL_ADDRESS_DEFAULT) {
}

case class PublicAssetTotalSizeLimit(value: UnsignedInt) {
  def asLong(): Long = value.value
}
