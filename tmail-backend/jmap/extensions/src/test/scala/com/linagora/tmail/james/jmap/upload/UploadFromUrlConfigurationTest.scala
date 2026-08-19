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

import org.apache.commons.configuration2.PropertiesConfiguration
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Test

import scala.jdk.CollectionConverters._

class UploadFromUrlConfigurationTest {
  @Test
  def shouldBeDisabledByDefault(): Unit = {
    val configuration = UploadFromUrlConfiguration.from(new PropertiesConfiguration)

    assertThat(configuration.enabled).isFalse
    assertThat(configuration.allowedSources.asJava).isEmpty
    assertThat(configuration.sourceResponseTimeout).isEqualTo(Duration.ofMinutes(1))
    assertThat(configuration.sourceTrustAllSslCerts).isFalse
  }

  @Test
  def shouldEnableWithoutSourcesAndSelectSsrfProtection(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.ENABLED_PROPERTY, true)

    val configuration = UploadFromUrlConfiguration.from(properties)

    assertThat(configuration.enabled).isTrue
    assertThat(configuration.usesSsrfProtection).isTrue
  }

  @Test
  def shouldParseCommaSeparatedSources(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.ALLOWED_SOURCES_PROPERTY,
      "https://drive.example.com, https://%-drive.twake.linagora.com")

    assertThat(UploadFromUrlConfiguration.from(properties).allowedSources.asJava).hasSize(2)
  }

  @Test
  def shouldParseSourceResponseTimeout(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.SOURCE_RESPONSE_TIMEOUT_PROPERTY, "2m")

    assertThat(UploadFromUrlConfiguration.from(properties).sourceResponseTimeout).isEqualTo(Duration.ofMinutes(2))
  }

  @Test
  def shouldRejectNonPositiveSourceResponseTimeout(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.SOURCE_RESPONSE_TIMEOUT_PROPERTY, "0s")

    assertThatThrownBy(() => UploadFromUrlConfiguration.from(properties))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessage("requirement failed: `upload.from.url.source.response.timeout` must be strictly positive")
  }

  @Test
  def shouldAllowToTrustAllSourceSslCertificatesWhenConfiguredWithAllowedSources(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.ALLOWED_SOURCES_PROPERTY, "https://drive.example.com")
    properties.setProperty(UploadFromUrlConfiguration.SOURCE_TRUST_ALL_SSL_CERTS_PROPERTY, true)

    assertThat(UploadFromUrlConfiguration.from(properties).sourceTrustAllSslCerts).isTrue
  }

  @Test
  def shouldRejectTrustAllSourceSslCertificatesWithoutAllowedSources(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.SOURCE_TRUST_ALL_SSL_CERTS_PROPERTY, true)

    assertThatThrownBy(() => UploadFromUrlConfiguration.from(properties))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessage("requirement failed: `upload.from.url.source.trust.all.ssl.certs` requires `upload.from.url.allowed.sources`")
  }

  @Test
  def shouldRejectInvalidConfiguredSourceWithoutEchoingIt(): Unit = {
    val properties = new PropertiesConfiguration
    properties.setProperty(UploadFromUrlConfiguration.ALLOWED_SOURCES_PROPERTY,
      "https://drive.example.com/files/secret-token")

    assertThatThrownBy(() => UploadFromUrlConfiguration.from(properties))
      .isInstanceOf(classOf[IllegalArgumentException])
      .hasMessage("Invalid `upload.from.url.allowed.sources` entry at index 0")
      .hasMessageNotContaining("secret-token")
  }
}
