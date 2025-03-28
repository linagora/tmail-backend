package com.linagora.tmail.mailet;


import java.io.IOException;

import com.google.common.base.Strings;
import com.linagora.tmail.conf.AIBotConfigLoader;
import dev.ai4j.openai4j.OpenAiHttpException;
import org.apache.mailet.MailetException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import reactor.core.publisher.Mono;
import com.linagora.tmail.conf.AIBotProperties;

public class AIRedactioanlHelper{
    private AIBotConfig config;
    private ChatLanguageModel chatLanguageModel;
    private final ChatLanguageModelFactory chatLanguageModelFactory;



    public AIRedactioanlHelper(ChatLanguageModelFactory chatLanguageModelFactory) {
        this.chatLanguageModelFactory = chatLanguageModelFactory;
    }
    public Mono<String> suggestContent(String userInput, String mailContent) throws OpenAiHttpException, MailetException, IOException {
        if (Strings.isNullOrEmpty(userInput) || Strings.isNullOrEmpty(mailContent)) {
            return Mono.error(new IllegalArgumentException("User input and mail content cannot be null or empty"));
        }
        //configuration de bot
        confuguerAiBot();

        ChatMessage systemMessage = new SystemMessage(
                "You are an advanced email suggestion AI. Your role is to act as the recipient of an email and provide a helpful, contextually appropriate response to it. " +
                        "Use the content of the email and incorporate the user's input to craft the response. Be polite, professional, and aligned with the tone of the email. " +
                        "Focus on addressing the key points raised in the email, while integrating additional information or suggestions provided by the user "
        );
        ChatMessage prompt = new UserMessage(
                "Email Content: \n" + mailContent + "\n" +
                        "User Input: \n" + userInput
        );
        String llmResponse = chatLanguageModel.generate(systemMessage, prompt)
                .content()
                .text();
        return Mono.just(llmResponse);
    }



    public void confuguerAiBot() throws MailetException, OpenAiHttpException, IOException {
        this.config= AIBotConfig.fromMailetConfig(AIBotConfigLoader.loadConfiguration());
        this.chatLanguageModel = createChatLanguageModelModel(config);

    }

    private ChatLanguageModel createChatLanguageModelModel(AIBotConfig AIBotConfig) {
        return chatLanguageModelFactory.createChatLanguageModel(AIBotConfig);
    }


    public AIBotConfig getConfig() {
        return config;
    }

    public ChatLanguageModel getChatLanguageModel() {
        return chatLanguageModel;
    }

    public ChatLanguageModelFactory getChatLanguageModelFactory() {
        return chatLanguageModelFactory;
    }
}
