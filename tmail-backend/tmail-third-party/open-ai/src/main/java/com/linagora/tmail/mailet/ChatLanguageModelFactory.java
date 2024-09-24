package com.linagora.tmail.mailet;

import java.net.URL;
import java.util.Optional;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ChatLanguageModelFactory {

    public ChatLanguageModel createChatLanguageModel(AIBotConfig config) {
        String apiKey = config.getApiKey();
        LlmModel llmModel = config.getLlmModel();
        Optional<URL> baseURLOpt = config.getBaseURL();

        return switch (llmModel.llm()) {
            case OPEN_AI -> createOpenAILanguageModel(apiKey, llmModel.modelName(), baseURLOpt.map(URL::toString).orElse(null));
        };
    }

    private ChatLanguageModel createOpenAILanguageModel(String apiKey, String modelName, String baseUrl) {
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .baseUrl(baseUrl)
                .build();
    }
}
