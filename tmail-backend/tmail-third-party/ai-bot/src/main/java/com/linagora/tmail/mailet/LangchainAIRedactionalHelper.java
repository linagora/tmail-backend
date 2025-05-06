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
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import jakarta.inject.Inject;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import dev.ai4j.openai4j.OpenAiHttpException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Mono;


public class LangchainAIRedactionalHelper implements AIRedactionalHelper {
    private final StreamingChatLanguageModel chatLanguageModel;

    @Inject
    public LangchainAIRedactionalHelper(StreamingChatLanguageModel chatLanguageModel) {
        this.chatLanguageModel = chatLanguageModel;
    }

    public Mono<String> suggestContent(String userInput, Optional<String> mailContent) throws OpenAiHttpException, IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(userInput), "User input cannot be null or empty");
        ChatMessage promptForContext = generateSystemMessage(mailContent);
        ChatMessage promptForUserInput = new UserMessage(userInput);

        List<ChatMessage> messages = Stream.concat(
                Stream.of(promptForContext, promptForUserInput),
                mailContent.stream().map(SystemMessage::new))
            .collect(ImmutableList.toImmutableList());

        return Mono.create(sink -> {
            chatLanguageModel.generate(messages, new StreamingResponseHandler() {
                StringBuilder result = new StringBuilder();

                @Override
                public void onComplete(Response response) {
                    sink.success(result.toString());
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }

                @Override
                public void onNext(String partialResponse) {
                    result.append(partialResponse);
                }
            });
        });
    }

    private ChatMessage generateSystemMessage(Optional<String> mailContent) {
        if (mailContent.isPresent()) {
            return new SystemMessage("You are an advanced email assistant AI. Your role is to analyze the given email content and generate a professional and contextually appropriate reply, as if you were the actual recipient of that email.\n\n" +
                "Please do not simply repeat or copy the email content. Process it, understand it, and respond accordingly.\n\n" +
                "Carefully address the key points raised in the message, while incorporating any relevant suggestions or information from the user.\n\n" +
                "Make sure your reply is polite, well-structured, and matches the tone and intent of the original message.\n\n" +
                "*** Important: Generate only one response and nothing else. Reply as the recipient of the email. ***");
        }
        return new SystemMessage("You are an advanced email assistant AI. Your task is to compose a professional and well-structured email based on the user's input.\n\n" +
            "Make sure the email clearly conveys the intended message and suggestions in an appropriate tone and language.\n\n" +
            "Generate only one version of the email, and act as the sender.");
        }
}
