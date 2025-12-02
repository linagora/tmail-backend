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
package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

import com.google.inject.Injector;

import nl.jqno.equalsverifier.EqualsVerifier;

public class AIBotConfigTest {
    private Injector injector;
    private AIBotConfig aiBotConfig;
    private AIRedactionalHelper aiRedactionalHelper;

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenApiKeyIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");

        assertThatThrownBy(() -> AIBotConfig.from(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No value for apiKey parameter was provided.");
    }

    @Test
    public void shouldThrowRuntimeExceptionWhenURLIsWrong() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "htp://example.com");

        assertThatThrownBy(() -> AIBotConfig.from(configuration))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid LLM API base URL");
    }

    @Test
    public void shouldVerifyCorrectConfiguration() throws Exception {
        //Arrange
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");
        configuration.addProperty("timeout", "5s");

        //act
        AIBotConfig expected = new AIBotConfig("sk-fakefakefakefakefakefakefakefake", new LlmModel("Lucie"), Optional.of(URI.create("https://chat.lucie.exemple.com").toURL()),
            Duration.ofSeconds(5));
        AIBotConfig actual = AIBotConfig.from(configuration);

        //Assertions
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    public void shouldAcceptBlankBaseUrl() throws Exception {
        PropertiesConfiguration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "sk-fakefakefakefakefakefakefakefake");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "");

        AIBotConfig expected = new AIBotConfig("sk-fakefakefakefakefakefakefakefake", new LlmModel("Lucie"), Optional.empty(),
            Duration.ofSeconds(10));
        AIBotConfig actual = AIBotConfig.from(configuration);

        assertThat(actual).isEqualTo(expected);
    }
    
    @Test
    void shouldRespectEqualsAndHashCodeContract() {
        EqualsVerifier.simple().forClass(AIBotConfig.class).verify();
    }
}


