package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.inject.Injector;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.Test;

public class AIBotConfigTest {
    private Injector injector;
    private AIBotConfig aiBotConfig;
    private AIRedactionalHelper aiRedactionalHelper;

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenApiKeyIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("botAddress", "gpt@localhost");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");

        assertThatThrownBy(() -> AIBotConfig.fromMailetConfig(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No value for apiKey parameter was provided.");
    }

    @Test
    public void shouldThrowIllegalArgumentExceptionWhenBotAdressIsNull() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "demo");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "https://chat.lucie.exemple.com");

        assertThatThrownBy(() -> AIBotConfig.fromMailetConfig(configuration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No value for botAddress parameter was provided.");
    }

    @Test
    public void shouldThrowRuntimeExceptionWhenURLIsWrong() {
        Configuration configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "demo");
        configuration.addProperty("botAddress", "gpt@localhost");
        configuration.addProperty("model", "Lucie");
        configuration.addProperty("baseURL", "htp://example.com");

        assertThatThrownBy(() -> AIBotConfig.fromMailetConfig(configuration))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Invalid LLM API base URL");
    }

}


