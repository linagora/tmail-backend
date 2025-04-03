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
import java.util.Optional;

import dev.langchain4j.data.message.ChatMessageType;
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
    private final ChatLanguageModel chatLanguageModel;

    @Inject
    public AIRedactionalHelper(ChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public Mono<String> suggestContent(String userInput, Optional<String> mailContent) throws OpenAiHttpException, MailetException, IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(userInput), "User input cannot be null or empty");
        ChatMessage promptForContext = generatePrompt(mailContent);
        ChatMessage promptForUserInput = new UserMessage(userInput);
        ChatMessage promptForMailContent = new SystemMessage("[EMAIL CONTENT] (Read-only): " + mailContent);
        String llmResponse = chatLanguageModel.generate(promptForContext, promptForUserInput, promptForMailContent)
                .content()
                .text();
        return Mono.just(llmResponse);
    }

    private ChatMessage generatePrompt(Optional<String> mailContent) {
        String prompt;
        if (mailContent.isPresent()) {
            prompt = "You are an advanced email assistant AI. Your role is to analyze the provided email content and generate a professional, contextually relevant response. " +
                "Act as the recipient, carefully addressing the key points raised while integrating any additional information or suggestions from the user. " +
                "Ensure the response is polite, well-structured, and aligned with the email’s tone and intent. ***generate only one option and act like the recipient of the email*** " ;
        } else {
            prompt = "You are an advanced email assistant AI. Your task is to compose a professional and well-structured email based on the user's input. " +
                "Ensure the email effectively conveys the provided information and suggestions in a clear and appropriate manner." +
                "Generate only one option and act like the sender of the email.";
        }
        return new SystemMessage(prompt);
    }

}
