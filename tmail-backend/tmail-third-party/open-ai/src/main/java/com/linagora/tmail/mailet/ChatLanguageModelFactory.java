package com.linagora.tmail.mailet;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

public class ChatLanguageModelFactory {
    private static final String OPEN_AI_BASE_URL = "https://api.openai.com/v1";
    private static final String LINAGORA_AI_BASE_URL = "https://ai.linagora.com/api/";

    public ChatLanguageModel createChatLanguageModel(MailBotConfig config) {
        String apiKey = config.getApiKey();
        LlmModel llmModel = config.getLlmModel();

        return switch (llmModel.llm()) {
            case OPEN_AI -> createOpenAILanguageModel(apiKey, llmModel.modelName(), OPEN_AI_BASE_URL);
            case LINAGORA_AI -> createOpenAILanguageModel(apiKey, llmModel.modelName(), LINAGORA_AI_BASE_URL);
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
