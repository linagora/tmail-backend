package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

import jakarta.mail.MessagingException;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class AIRedactionalHelperTest {
    private AIRedactionalHelper aiRedactioanlHelper;
    private Configuration configuration;
    private AIBotConfig aiBotConfig;

    @BeforeEach
    void setUp() {
        configuration = new PropertiesConfiguration();
        configuration.addProperty("apiKey", "demo");
        configuration.addProperty("botAddress", "gpt@localhost");
        configuration.addProperty("model", "gemini-2.0-flash");
        configuration.addProperty("baseURL", "https://generativelanguage.googleapis.com/v1beta");
        aiBotConfig= AIBotConfig.fromMailetConfig(configuration);
        aiRedactioanlHelper = new AIRedactionalHelper(aiBotConfig, new ChatLanguageModelFactory());
    }

    @Test
    void testSuggestContentNullInput() {

        ChatLanguageModelFactory mockFactory = mock(ChatLanguageModelFactory.class);


        assertThrows(IllegalArgumentException.class, () ->
                aiRedactioanlHelper.suggestContent(null, "Valid content").block());
    }

    @Test
    void initShouldCheckChatModel() throws MessagingException , IOException {

        assertThat(aiRedactioanlHelper.getChatLanguageModel()).isNotNull();

    }

    @Test
    void initShouldVerifyConfigCredentials() throws MessagingException , IOException {

        assertThat(aiRedactioanlHelper.getConfig().getApiKey()).isEqualTo("demo");
        assertThat(aiRedactioanlHelper.getConfig().getBotAddress()).isEqualTo("gpt@localhost");
        assertThat(aiRedactioanlHelper.getConfig().getLlmModel().modelName()).isEqualTo("gemini-2.0-flash");
        assertThat(aiRedactioanlHelper.getConfig().getBaseURL().get().toString()).isEqualTo("https://generativelanguage.googleapis.com/v1beta");
    }

    @Test
    void shoulthrouwOpenAiHttpException() throws Exception {

        configuration.setProperty("baseURL", "https://generativelanguage.googleapis.com");
        aiBotConfig= AIBotConfig.fromMailetConfig(configuration);
        aiRedactioanlHelper = new AIRedactionalHelper(aiBotConfig, new ChatLanguageModelFactory());
        String userInput="email content";
        String mailContent="I want to know if your ready to go by 6pm ?";
        assertThatThrownBy(() -> aiRedactioanlHelper.suggestContent(userInput, mailContent))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldReplyToSender() throws Exception {

        String userInput="email content";
        String mailContent="I want to know if your ready to go by 6pm ?";
        String output= aiRedactioanlHelper.suggestContent(userInput,mailContent).block();;
        assertThat(output).isNotNull();
        assertThat(output).isInstanceOf(String.class);
    }

}
