package com.linagora.tmail.james.jmap

import java.util.Locale

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.{CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_DEFAULT, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, TICKET_IP_VALIDATION_ENABLED}
import com.linagora.tmail.james.jmap.method.CalendarEventReplySupportedLanguage.LANGUAGE_DEFAULT
import com.linagora.tmail.james.jmap.model.LanguageLocation
import eu.timepit.refined
import org.apache.commons.configuration2.Configuration
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.server.core.MissingArgumentException
import org.apache.james.util.Size

import scala.util.{Failure, Success, Try}

object JMAPExtensionConfiguration {
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY: String = "public.asset.total.size"
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT: PublicAssetTotalSizeLimit = PublicAssetTotalSizeLimit.of(Size.of(20L, Size.Unit.M)).get
  val TICKET_IP_VALIDATION_PROPERTY: String = "authentication.strategy.rfc8621.tickets.ip.validation.enabled"
  val TICKET_IP_VALIDATION_ENABLED: TicketIpValidationEnable = TicketIpValidationEnable(true)
  val CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_PROPERTY: String = "calendarEvent.reply.supportedLanguages"
  val CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_DEFAULT: CalendarEventReplySupportedLanguagesConfig = CalendarEventReplySupportedLanguagesConfig(Set(LANGUAGE_DEFAULT))

  val SUPPORT_MAIL_ADDRESS_PROPERTY: String = "support.mail.address"

  def from(configuration: Configuration): JMAPExtensionConfiguration = {
    val publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = Option(configuration.getString(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY, null))
      .map(Size.parse)
      .map(PublicAssetTotalSizeLimit.of(_).get)
      .getOrElse(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT)

    val supportMailAddressOpt: Option[MailAddress] =
      Option(configuration.getString(SUPPORT_MAIL_ADDRESS_PROPERTY, null))
        .map(s => new MailAddress(s))

    val ticketIpValidationEnable: TicketIpValidationEnable = Option(configuration.getBoolean(TICKET_IP_VALIDATION_PROPERTY, null))
      .map(TicketIpValidationEnable(_))
      .getOrElse(TICKET_IP_VALIDATION_ENABLED)

    val calendarEventReplySupportedLanguagesConfig: CalendarEventReplySupportedLanguagesConfig = CalendarEventReplySupportedLanguagesConfig(
      Try(configuration.getStringArray(CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_PROPERTY).toSet)
        .map(_.map(lgTag => LanguageLocation.detectLocale(lgTag) match {
          case Success(value) => value
          case Failure(error) => throw new MissingArgumentException("Invalid language tag in the configuration file." + error.getMessage)
        }))
        .fold(_ => Set.empty, identity))

    JMAPExtensionConfiguration(publicAssetTotalSizeLimit, supportMailAddressOpt, ticketIpValidationEnable,
      calendarEventReplySupportedLanguagesConfig)
  }
}

object PublicAssetTotalSizeLimit {
  def of(size: Size): Try[PublicAssetTotalSizeLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalSizeLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class JMAPExtensionConfiguration(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT,
                                      supportMailAddress: Option[MailAddress] = Option.empty,
                                      ticketIpValidationEnable: TicketIpValidationEnable = TICKET_IP_VALIDATION_ENABLED,
                                      calendarEventReplySupportedLanguagesConfig: CalendarEventReplySupportedLanguagesConfig = CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_DEFAULT) {
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

case class TicketIpValidationEnable(value: Boolean)

case class CalendarEventReplySupportedLanguagesConfig(supportedLanguages: Set[Locale])