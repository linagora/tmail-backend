package com.linagora.tmail.mailet;

import java.net.URL;
import java.util.Optional;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ChatLanguageModelFactory {
    private static final String USE_DEFAULT_BASE_URL = "";

    public ChatLanguageModel createChatLanguageModel(AIBotConfig config) {
        String apiKey = config.getApiKey();
        LlmModel llmModel = config.getLlmModel();
        Optional<URL> baseURLOpt = config.getBaseURL();

        return createOpenAILanguageModel(apiKey, llmModel.modelName(), baseURLOpt.map(URL::toString).orElse(USE_DEFAULT_BASE_URL));
    }

    private ChatLanguageModel createOpenAILanguageModel(String apiKey, String modelName, String baseUrl) {
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .modelName(modelName)
            .baseUrl(baseUrl)
            .build();
    }
}
