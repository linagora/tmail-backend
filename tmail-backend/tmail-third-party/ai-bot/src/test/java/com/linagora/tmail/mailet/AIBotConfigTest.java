package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.assertNotNull;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.linagora.tmail.conf.AIBotConfigModule;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.james.utils.PropertiesProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class AIBotConfigTest {
    private Injector injector;
    private AIBotConfig aiBotConfig;
    private AIRedactionalHelper aiRedactionalHelper;


//    @Test
//    void testAIBotConfigNotNull() {
//        // Vérifier que la configuration AI Bot est bien injectée
//        assertNotNull(aiBotConfig);
//    }
//
//    @Test
//    void testAIRedactionalHelperInjection() {
//        // Vérifier que AI Redactional Helper est bien injecté
//        assertNotNull(aiRedactionalHelper);
//    }
//
//    @Test
//    void testChatLanguageModelInjection() {
//        // Vérifier que ChatLanguageModel est bien injecté dans AIRedactionalHelper
//        ChatLanguageModel chatModel = aiRedactionalHelper.getChatLanguageModel();
//        assertNotNull(chatModel);
//    }
//
//    @Test
//    void testChatLanguageModelFactoryInjection() {
//        // Vérifier que ChatLanguageModelFactory est bien injecté
//        ChatLanguageModelFactory factory = aiRedactionalHelper.getChatLanguageModelFactory();
//        assertNotNull(factory);
//    }



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


