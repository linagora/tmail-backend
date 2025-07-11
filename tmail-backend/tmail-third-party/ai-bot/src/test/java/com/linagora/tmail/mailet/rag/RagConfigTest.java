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
package com.linagora.tmail.mailet.rag;

import com.linagora.tmail.mailet.AIBotConfig;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class RagConfigTest {

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenApiKeyIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("ragondin.url", "https://ragondin.linagora.com");
        configuration.addProperty("ragondin.ssl.trust.all.certs", "true");

        assertThatThrownBy(() -> RagConfig.from(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No value for ragondin.Token parameter was provided.");
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenMissingURL() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("ragondin.Token", "fake-token");
        configuration.addProperty("ragondin.ssl.trust.all.certs", "true");
        configuration.addProperty("ragondin.partition.pattern", "{localPart}.twake.{domainName}");

        assertThatThrownBy(() -> RagConfig.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value for ragondin.url parameter was provided.");
    }

    @Test
    public void shouldThrowRuntimeExceptionWhenUrlFormatIsWorng() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("ragondin.url", "htp://ragondin.linagora.com");
        configuration.addProperty("ragondin.Token", "fake-token");
        configuration.addProperty("ragondin.ssl.trust.all.certs", "true");
        configuration.addProperty("ragondin.partition.pattern", "{localPart}.twake.{domainName}");

        assertThatThrownBy(() -> RagConfig.from(configuration))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Invalid RAG API base URL");
    }

    @Test
    public void shouldConfigureTrustAllCertsTrueWhenMissingTrustAllCerts() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("ragondin.url", "https://ragondin.linagora.com");
        configuration.addProperty("ragondin.Token", "fake-token");
        configuration.addProperty("ragondin.partition.pattern", "{localPart}.twake.{domainName}");
        RagConfig actual = RagConfig.from(configuration);

        assertThat(actual.getTrustAllCertificates()).isTrue();
    }

    @Test
    public void shouldConfigureDefaultPartitionPatternWhenMissingPartitionPatternProperty() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("ragondin.url", "https://ragondin.linagora.com");
        configuration.addProperty("ragondin.Token", "fake-token");
        configuration.addProperty("ragondin.ssl.trust.all.certs", "true");
        RagConfig actual = RagConfig.from(configuration);

        assertThat(actual.getPartitionPattern()).isEqualTo(RagConfig.DEFAULT_PARTITION_PATTERN);
    }

    @Test
    public void shouldVerifyCorrectConfiguration() throws Exception {
        //Arrange
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("ragondin.url", "https://ragondin.linagora.com");
        configuration.addProperty("ragondin.Token", "fake-token");
        configuration.addProperty("ragondin.ssl.trust.all.certs", "true");
        configuration.addProperty("ragondin.partition.pattern", "{localPart}.twake.{domainName}");

        //act
        RagConfig expected = new RagConfig("fake-token", true, Optional.of(URI.create("https://ragondin.linagora.com").toURL()), "{localPart}.twake.{domainName}");
        RagConfig actual = RagConfig.from(configuration);

        //Assertions
        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    void shouldRespectEqualsAndHashCodeContract() {
        EqualsVerifier.simple().forClass(AIBotConfig.class).verify();
    }
}