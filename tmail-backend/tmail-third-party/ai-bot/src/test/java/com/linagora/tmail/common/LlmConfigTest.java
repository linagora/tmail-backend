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
package com.linagora.tmail.common;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.net.URI;
import java.time.Duration;

import com.linagora.tmail.common.chatlanguagemodel.LlmConfig;
import com.linagora.tmail.common.chatlanguagemodel.LlmModel;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class LlmConfigTest {
    @Test
    void shouldThrowIllegalArgumentExceptionWhenApiKeyIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No value for apiKey parameter was provided.");
    }

    @Test
    void shouldThrowRuntimeExceptionWhenURLIsWrong() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "htp://example.com");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid LLM API base URL");
    }

    @Test
    void shouldVerifyCorrectConfiguration() throws Exception {
        //Arrange
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");
        configuration.addProperty("timeout", "5s");

        //act
        LlmConfig expected = new LlmConfig("sk-fakefakefakefakefakefakefakefake", new LlmModel("Lucie"), URI.create("https://chat.lucie.exemple.com").toURL(),
            Duration.ofSeconds(5));
        LlmConfig actual = LlmConfig.from(configuration);

        //Assertions
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldRejectBlankBaseUrl() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value for baseURL parameter was provided.");
    }

    @Test
    void shouldRejectWhitespaceBaseUrl() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("baseURL", "   ");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value for baseURL parameter was provided.");
    }

    @Test
    void shouldRejectMissingBaseUrl() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("No value for baseURL parameter was provided.");
    }

    @Test
    void shouldRejectHttpBaseUrlByDefault() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("baseURL", "http://chat.lucie.exemple.com");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("baseURL.allowHttp");
    }

    @Test
    void shouldAcceptHttpBaseUrlWhenExplicitlyAllowed() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "http://localhost:11434/v1");
        configuration.addProperty("baseURL.allowHttp", "true");

        assertThat(LlmConfig.from(configuration))
            .isEqualTo(new LlmConfig("sk-fakefakefakefakefakefakefakefake", new LlmModel("Lucie"),
                URI.create("http://localhost:11434/v1").toURL(), Duration.ofSeconds(10)));
    }

    @Test
    void shouldRejectNonHttpSchemes() {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("baseURL", "ftp://chat.lucie.exemple.com");
        configuration.addProperty("baseURL.allowHttp", "true");

        assertThatThrownBy(() -> LlmConfig.from(configuration))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid LLM API base URL");
    }

    @Test
    void shouldSupportNoModel() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");

        LlmConfig expected = new LlmConfig("sk-fakefakefakefakefakefakefakefake", new LlmModel(""),
            URI.create("https://chat.lucie.exemple.com").toURL(), Duration.ofSeconds(10));
        LlmConfig actual = LlmConfig.from(configuration);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    void shouldRespectEqualsAndHashCodeContract() {
        EqualsVerifier.simple().forClass(LlmConfig.class).verify();
    }
}


