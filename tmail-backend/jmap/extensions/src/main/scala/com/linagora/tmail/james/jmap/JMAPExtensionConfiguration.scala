package com.linagora.tmail.james.jmap

import java.net.{URI, URL}
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Locale

import com.google.common.base.Preconditions
import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.{CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_DEFAULT, PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, TICKET_IP_VALIDATION_ENABLED}
import com.linagora.tmail.james.jmap.method.CalendarEventReplySupportedLanguage.LANGUAGE_DEFAULT
import com.linagora.tmail.james.jmap.model.LanguageLocation
import eu.timepit.refined
import org.apache.commons.configuration2.Configuration
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.UnsignedInt.{UnsignedInt, UnsignedIntConstraint}
import org.apache.james.server.core.MissingArgumentException
import org.apache.james.util.{DurationParser, Size}

import scala.util.{Failure, Success, Try}

object JMAPExtensionConfiguration {
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY: String = "public.asset.total.size"
  val PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT: PublicAssetTotalSizeLimit = PublicAssetTotalSizeLimit.of(Size.of(20L, Size.Unit.M)).get
  val TICKET_IP_VALIDATION_PROPERTY: String = "authentication.strategy.rfc8621.tickets.ip.validation.enabled"
  val TICKET_IP_VALIDATION_ENABLED: TicketIpValidationEnable = TicketIpValidationEnable(true)
  val CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_PROPERTY: String = "calendarEvent.reply.supportedLanguages"
  val CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_DEFAULT: CalendarEventReplySupportedLanguagesConfig = CalendarEventReplySupportedLanguagesConfig(Set(LANGUAGE_DEFAULT))

  val SUPPORT_MAIL_ADDRESS_PROPERTY: String = "support.mail.address"
  val SUPPORT_HTTP_LINK_PROPERTY: String = "support.httpLink"

  def from(configuration: Configuration): JMAPExtensionConfiguration = {
    val publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = Option(configuration.getString(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_PROPERTY, null))
      .map(Size.parse)
      .map(PublicAssetTotalSizeLimit.of(_).get)
      .getOrElse(PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT)

    val supportMailAddressOpt: Option[MailAddress] =
      Option(configuration.getString(SUPPORT_MAIL_ADDRESS_PROPERTY, null))
        .map(s => new MailAddress(s))

    val supportHttpLinkOpt: Option[URL] = Option(configuration.getString(SUPPORT_HTTP_LINK_PROPERTY, null))
      .map(httpLink => Try(new URI(httpLink).toURL)
        .getOrElse(throw new MissingArgumentException(s"Invalid `$SUPPORT_HTTP_LINK_PROPERTY` in the jmap configuration file: " + httpLink)))

    Preconditions.checkArgument(!(supportMailAddressOpt.isDefined && supportHttpLinkOpt.isDefined),
      s"Both `$SUPPORT_MAIL_ADDRESS_PROPERTY` and `$SUPPORT_HTTP_LINK_PROPERTY` must not be defined at the same time.".asInstanceOf[Object])

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

    val emailRecoveryActionConfiguration: EmailRecoveryActionConfiguration = EmailRecoveryActionConfiguration.from(configuration)

    val webFingerConfiguration: WebFingerConfiguration = WebFingerConfiguration.parse(configuration)

    JMAPExtensionConfiguration(publicAssetTotalSizeLimit = publicAssetTotalSizeLimit,
      supportMailAddress = supportMailAddressOpt,
      supportHttpLink = supportHttpLinkOpt,
      ticketIpValidationEnable = ticketIpValidationEnable,
      calendarEventReplySupportedLanguagesConfig = calendarEventReplySupportedLanguagesConfig,
      emailRecoveryActionConfiguration = emailRecoveryActionConfiguration,
      webFingerConfiguration = webFingerConfiguration)
  }
}

object PublicAssetTotalSizeLimit {
  def of(size: Size): Try[PublicAssetTotalSizeLimit] = refined.refineV[UnsignedIntConstraint](size.asBytes()) match {
    case Right(value) => Success(PublicAssetTotalSizeLimit(value))
    case Left(error) => Failure(new NumberFormatException(error))
  }
}

case class JMAPExtensionConfiguration(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit = PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT,
                                      supportMailAddress: Option[MailAddress] = None,
                                      supportHttpLink: Option[URL] = None,
                                      ticketIpValidationEnable: TicketIpValidationEnable = TICKET_IP_VALIDATION_ENABLED,
                                      calendarEventReplySupportedLanguagesConfig: CalendarEventReplySupportedLanguagesConfig = CALENDAR_EVENT_REPLY_SUPPORTED_LANGUAGES_DEFAULT,
                                      emailRecoveryActionConfiguration: EmailRecoveryActionConfiguration = EmailRecoveryActionConfiguration.DEFAULT,
                                      webFingerConfiguration: WebFingerConfiguration = WebFingerConfiguration.DEFAULT) {
  def this(publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit) = {
    this(publicAssetTotalSizeLimit, Option.empty)
  }
}

case class PublicAssetTotalSizeLimit(value: UnsignedInt) {
  def asLong(): Long = value.value
}

case class TicketIpValidationEnable(value: Boolean)

case class CalendarEventReplySupportedLanguagesConfig(supportedLanguages: Set[Locale])

object EmailRecoveryActionConfiguration {
  val DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST: Long = 5
  val DEFAULT_RESTORATION_HORIZON: Duration = DurationParser.parse("15", ChronoUnit.DAYS)
  val DEFAULT: EmailRecoveryActionConfiguration = EmailRecoveryActionConfiguration(DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST, DEFAULT_RESTORATION_HORIZON)

  def from(jmapConfiguration: Configuration): EmailRecoveryActionConfiguration = {
    val maxEmailRecoveryPerRequest: Long = jmapConfiguration.getLong("emailRecoveryAction.maxEmailRecoveryPerRequest", DEFAULT_MAX_EMAIL_RECOVERY_PER_REQUEST)

    val restorationHorizon: Duration = Option(jmapConfiguration.getString("emailRecoveryAction.restorationHorizon", null))
      .map(DurationParser.parse)
      .getOrElse(DEFAULT_RESTORATION_HORIZON)

    EmailRecoveryActionConfiguration(maxEmailRecoveryPerRequest, restorationHorizon)
  }
}

case class EmailRecoveryActionConfiguration(maxEmailRecoveryPerRequest: Long, restorationHorizon: Duration)

object WebFingerConfiguration {
  val DEFAULT: WebFingerConfiguration = WebFingerConfiguration(None)

  def parse(configuration: Configuration): WebFingerConfiguration =
    WebFingerConfiguration(Option(configuration.getString("oidc.provider.url", null)).map(new URI(_).toURL))
}

case class WebFingerConfiguration(openIdUrl: Option[URL])