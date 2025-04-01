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

import java.io.IOException;

import jakarta.inject.Inject;

import org.apache.mailet.MailetException;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import reactor.core.publisher.Mono;


public class AIRedactionalHelper {
    private final AIBotConfig config;
    private final ChatLanguageModel chatLanguageModel;
    private final ChatLanguageModelFactory chatLanguageModelFactory;


    @Inject
    public AIRedactionalHelper(AIBotConfig config, ChatLanguageModelFactory chatLanguageModelFactory) {
        this.config = config;
        this.chatLanguageModelFactory = chatLanguageModelFactory;
        this.chatLanguageModel = createChatLanguageModelModel(config);
    }

    public Mono<String> suggestContent(String userInput, String mailContent) throws OpenAiHttpException, MailetException, IOException {

        Preconditions.checkArgument(!Strings.isNullOrEmpty(userInput), "User input cannot be null or empty");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mailContent), "Mail content cannot be null or empty");

        ChatMessage systemMessage = new SystemMessage(
                "You are an advanced email suggestion AI. Your role is to act as the recipient of an email and provide a helpful, contextually appropriate response to it. " +
                        "Use the content of the email and incorporate the user's input to craft the response. Be polite, professional, and aligned with the tone of the email. " +
                        "Focus on addressing the key points raised in the email, while integrating additional information or suggestions provided by the user ");
        ChatMessage prompt = new UserMessage(
                "Email Content: \n" + mailContent + "\n" + "User Input: \n" + userInput);
        String llmResponse = chatLanguageModel.generate(systemMessage, prompt)
                .content()
                .text();
        return Mono.just(llmResponse);
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

}
