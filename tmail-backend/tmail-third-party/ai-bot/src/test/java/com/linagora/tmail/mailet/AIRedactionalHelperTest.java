package com.linagora.tmail.mailet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.PropertiesConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        ChatLanguageModel chatLanguageModel = new ChatLanguageModelFactory().createChatLanguageModel(aiBotConfig);
        aiRedactioanlHelper = new AIRedactionalHelper(chatLanguageModel);
    }

    @Test
    void testSuggestContentNullInput() {
        assertThatThrownBy(() ->
            aiRedactioanlHelper.suggestContent(null, "Valid content").block()
        ).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shoulThrouwOpenAiHttpException() throws Exception {
        configuration.setProperty("baseURL", "https://generativelanguage.googleapis.com");
        aiBotConfig= AIBotConfig.fromMailetConfig(configuration);
        ChatLanguageModel chatLanguageModel = new ChatLanguageModelFactory().createChatLanguageModel(aiBotConfig);
        aiRedactioanlHelper = new AIRedactionalHelper(chatLanguageModel);
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
