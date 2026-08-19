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
 *******************************************************************/

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

package com.linagora.tmail.james.jmap.upload

import java.time.Duration
import java.time.temporal.ChronoUnit

import org.apache.commons.configuration2.Configuration
import org.apache.james.util.DurationParser
import org.slf4j.{Logger, LoggerFactory}

object UploadFromUrlConfiguration {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[UploadFromUrlConfiguration])

  val ENABLED_PROPERTY: String = "upload.from.url.enabled"
  val ALLOWED_SOURCES_PROPERTY: String = "upload.from.url.allowed.sources"
  val SOURCE_RESPONSE_TIMEOUT_PROPERTY: String = "upload.from.url.source.response.timeout"
  val SOURCE_TRUST_ALL_SSL_CERTS_PROPERTY: String = "upload.from.url.source.trust.all.ssl.certs"
  val DEFAULT_SOURCE_RESPONSE_TIMEOUT: Duration = Duration.ofMinutes(1)
  val DISABLED: UploadFromUrlConfiguration = UploadFromUrlConfiguration(enabled = false,
    allowedSources = Seq.empty,
    sourceResponseTimeout = DEFAULT_SOURCE_RESPONSE_TIMEOUT)

  private def logWarnings(configuration: UploadFromUrlConfiguration): UploadFromUrlConfiguration = {
    if (configuration.isEnabled && configuration.usesSsrfProtection) {
      LOGGER.warn("JMAP upload from URL is enabled without allowed sources; SSRF-protected HTTPS/443 mode is active")
    }
    if (configuration.isEnabled && configuration.sourceTrustAllSslCerts) {
      LOGGER.warn("JMAP upload from URL trusts all source SSL certificate chains; use only for development or private PKI")
    }
    configuration
  }

  def from(configuration: Configuration): UploadFromUrlConfiguration = {
    val enabled = configuration.getBoolean(ENABLED_PROPERTY, false)
    val configuredSources: Seq[String] = Option(configuration.getStringArray(ALLOWED_SOURCES_PROPERTY))
      .toSeq
      .flatMap(_.toSeq)
      .flatMap(_.split(","))
      .map(_.trim)
      .filter(_.nonEmpty)

    val allowedSources: Seq[AllowedRemoteSource] = configuredSources.zipWithIndex.map {
      case (value, index) => AllowedRemoteSource.parse(value)
        .fold(cause => throw new IllegalArgumentException(s"Invalid `$ALLOWED_SOURCES_PROPERTY` entry at index $index", cause), identity)
    }
    val sourceResponseTimeout: Duration = Option(configuration.getString(SOURCE_RESPONSE_TIMEOUT_PROPERTY, null))
      .map(DurationParser.parse(_, ChronoUnit.SECONDS))
      .getOrElse(DEFAULT_SOURCE_RESPONSE_TIMEOUT)
    val sourceTrustAllSslCerts = configuration.getBoolean(SOURCE_TRUST_ALL_SSL_CERTS_PROPERTY, false)
    require(!sourceResponseTimeout.isZero && !sourceResponseTimeout.isNegative,
      s"`$SOURCE_RESPONSE_TIMEOUT_PROPERTY` must be strictly positive")
    require(!sourceTrustAllSslCerts || allowedSources.nonEmpty,
      s"`$SOURCE_TRUST_ALL_SSL_CERTS_PROPERTY` requires `$ALLOWED_SOURCES_PROPERTY`")

    logWarnings(UploadFromUrlConfiguration(enabled, allowedSources, sourceResponseTimeout, sourceTrustAllSslCerts))
  }
}

case class UploadFromUrlConfiguration(enabled: Boolean,
                                      allowedSources: Seq[AllowedRemoteSource],
                                      sourceResponseTimeout: Duration = UploadFromUrlConfiguration.DEFAULT_SOURCE_RESPONSE_TIMEOUT,
                                      sourceTrustAllSslCerts: Boolean = false) {
  def isEnabled: Boolean = enabled

  def usesSsrfProtection: Boolean = allowedSources.isEmpty
}
