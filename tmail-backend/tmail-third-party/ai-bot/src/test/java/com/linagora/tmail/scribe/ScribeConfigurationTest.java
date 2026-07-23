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
package com.linagora.tmail.scribe;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.net.URI;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

public class ScribeConfigurationTest {

    private static void assertConfiguration(PropertiesConfiguration configuration,
                                            String expectedToken,
                                            String expectedUrl,
                                            boolean expectedTrustAllCerts) throws Exception {
        ScribeConfiguration expected = new ScribeConfiguration(
            expectedToken,
            Optional.of(URI.create(expectedUrl).toURL()),
            expectedTrustAllCerts);
        ScribeConfiguration actual = ScribeConfiguration.from(configuration);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenApiKeyIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.url", "https://test.com");
        configuration.addProperty("scribe.ssl.trust.all.certs", "true");

        assertThatThrownBy(() -> ScribeConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value for scribe.token parameter was provided.");
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenMissingURL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.token", "fake-token");
        configuration.addProperty("scribe.ssl.trust.all.certs", "true");

        assertThatThrownBy(() -> ScribeConfiguration.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value for scribe.url parameter was provided.");
    }

    @Test
    public void shouldFallbackToLegacyPropertiesWhenNewOnesAreMissing() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "legacy-token");
        configuration.addProperty("baseURL", "https://legacy.example.com");

        assertConfiguration(configuration, "legacy-token", "https://legacy.example.com", true);
    }

    @Test
    public void shouldPreferNewPropertiesOverLegacyOnes() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.token", "new-token");
        configuration.addProperty("scribe.url", "https://new.example.com");
        configuration.addProperty("apiKey", "legacy-token");
        configuration.addProperty("baseURL", "https://legacy.example.com");

        assertConfiguration(configuration, "new-token", "https://new.example.com", true);
    }

    @Test
    public void shouldThrowRuntimeExceptionWhenUrlFormatIsWorng() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.url", "htp://test.linagora.com");
        configuration.addProperty("scribe.token", "fake-token");
        configuration.addProperty("scribe.ssl.trust.all.certs", "true");

        assertThatThrownBy(() -> ScribeConfiguration.from(configuration))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Invalid Scribe API base URL");
    }

    @Test
    public void shouldConfigureTrustAllCertsTrueWhenMissingTrustAllCerts() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.url", "https://test.com");
        configuration.addProperty("scribe.token", "fake-token");
        configuration.addProperty("scribe.partition.pattern", "{localPart}.twake.{domainName}");
        ScribeConfiguration actual = ScribeConfiguration.from(configuration);

        assertThat(actual.trustAllCertificates()).isTrue();
    }

    @Test
    public void shouldVerifyCorrectConfiguration() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("scribe.url", "https://test.linagora.com");
        configuration.addProperty("scribe.token", "fake-token");
        configuration.addProperty("scribe.ssl.trust.all.certs", "true");

        assertConfiguration(configuration, "fake-token", "https://test.linagora.com", true);
    }
}