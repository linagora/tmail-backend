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
import java.util.concurrent.CompletableFuture;

import jakarta.inject.Inject;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.apache.james.core.MailAddress;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.server.core.MimeMessageInputStream;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.mailet.Mail;
import org.apache.mailet.StorageDirective;
import org.apache.mailet.base.GenericMailet;

import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

public class LlmMailPrioritizationClassifier extends GenericMailet {
    private static final String NEEDS_ACTION_FLAG = "needs-action";

    private final UsersRepository usersRepository;
    private final StreamingChatLanguageModel chatLanguageModel;
    private final HtmlTextExtractor htmlTextExtractor;
    private final StorageDirective addNeedsActionFlag;

    @Inject
    public LlmMailPrioritizationClassifier(UsersRepository usersRepository,
                                           StreamingChatLanguageModel chatLanguageModel,
                                           HtmlTextExtractor htmlTextExtractor) {
        this.usersRepository = usersRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.htmlTextExtractor = htmlTextExtractor;
        this.addNeedsActionFlag = StorageDirective.builder()
            .keywords(Optional.of(ImmutableList.of(NEEDS_ACTION_FLAG)))
            .build();
    }

    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are an email-triage classifier. Your ONLY task is to evaluate whether an incoming email requires immediate human action by the recipient. 
        Return strictly one word: "YES" or "NO". Do not add explanations or any additional text.
        An email requires immediate action (YES) only if it reports urgent issues (like production failures or security incidents), time-sensitive requests or approvals (with clear deadlines), or payment/billing problems that might lead to service disruption soon. 
        Return NO for newsletters, marketing, spam, general updates, casual conversations, or unclear/ambiguous messages. If the email looks like spam or phishing, always return NO.
        """;

    @Override
    public void init() {
        // TODO make system prompt configurable
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        String userPrompt = buildUserPrompt(mail);
        String llmOutput = askLlm(DEFAULT_SYSTEM_PROMPT, userPrompt);
        boolean actionRequired = isActionRequired(llmOutput);

        if (actionRequired) {
            mail.getRecipients()
                .forEach(addNeedsActionFlag(mail));
        }
    }

    private String buildUserPrompt(Mail mail) throws MessagingException {
        try {
            MimeMessage mimeMessage = mail.getMessage();
            String from = Optional.ofNullable(mimeMessage.getHeader("From", ",")).orElse("");
            String to = Optional.ofNullable(mimeMessage.getHeader("To", ",")).orElse("");
            String subject = Strings.nullToEmpty(mimeMessage.getSubject());

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("From: ").append(from).append("\n");
            stringBuilder.append("To: ").append(to).append("\n");
            stringBuilder.append("Subject: ").append(subject).append("\n\n");
            stringBuilder.append(extractBodyContent(mimeMessage)).append("\n\n");
            stringBuilder.append("Does this email require immediate action? Respond only with YES or NO.");
            return stringBuilder.toString();
        }  catch (IOException e) {
            throw new MessagingException("Error while building LLM prompt", e);
        }
    }

    private String extractBodyContent(MimeMessage mimeMessage) throws MessagingException, IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        MessageContentExtractor.MessageContent messageContent = new MessageContentExtractor()
            .extract(defaultMessageBuilder.parseMessage(new MimeMessageInputStream(mimeMessage)));

        return messageContent.extractMainTextContent(htmlTextExtractor)
            .orElse("");
    }

    private String askLlm(String systemPrompt, String userPrompt) throws MessagingException {
        ChatMessage systemMessage = new SystemMessage(systemPrompt);
        ChatMessage userMessage = new UserMessage(userPrompt);
        List<ChatMessage> messages = List.of(systemMessage, userMessage);

        CompletableFuture<String> future = new CompletableFuture<>();

        chatLanguageModel.generate(messages, new StreamingResponseHandler() {
            private final StringBuilder result = new StringBuilder();

            @Override
            public void onNext(String token) {
                result.append(token);
            }

            @Override
            public void onComplete(Response response) {
                future.complete(result.toString());
            }

            @Override
            public void onError(Throwable error) {
                future.completeExceptionally(error);
            }
        });

        try {
            return future.get();
        } catch (Exception e) {
            throw new MessagingException("Error while calling LLM", e);
        }
    }

    private boolean isActionRequired(String llmOutput) {
        return Optional.ofNullable(llmOutput)
            .map(output -> output.contains("YES"))
            .orElse(false);
    }

    public ThrowingConsumer<MailAddress> addNeedsActionFlag(Mail mail) {
        return recipient -> addNeedsActionFlag.encodeAsAttributes(usersRepository.getUsername(recipient))
            .forEach(mail::setAttribute);
    }
}
