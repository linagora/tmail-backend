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
import jakarta.mail.internet.MimeUtility;

import org.apache.james.core.MailAddress;
import org.apache.james.metrics.api.MetricFactory;
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

import com.github.fge.lambdas.Throwing;
import com.github.fge.lambdas.consumers.ThrowingConsumer;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

/**
 * <b>LlmMailPrioritizationClassifier</b> is a mailet that uses a Large Language Model (LLM)
 * to determine whether an incoming email requires immediate user action. It analyzes the
 * From, To, Subject, and main text content of the message (HTML is converted to plain text),
 * and asks the configured LLM to answer strictly “YES” or “NO”.
 *
 * <p>
 * When the LLM replies “YES”, the mailet adds a <code>needs-action</code> keyword to the
 * message for each recipient. This allows clients to highlight urgent emails.
 * </p>
 *
 * <p>
 * The classifier is guided by a system prompt. If the optional
 * <b>systemPrompt</b> parameter is provided, it overrides the built-in default prompt.
 * </p>
 *
 * <h3>Configuration</h3>
 * <p>
 * The LLM model, endpoint, and API key are configured globally in <code>ai.properties</code>.
 * The mailet has configurable parameters:
 * </p>
 * <ul>
 *     <li><b>systemPrompt</b> (optional): Custom instruction for the classifier.</li>
 *     <li><b>maxReportBodyLength</b> (optional, default to 4000): Maximum email body length to report to LLM.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * <mailet match="com.linagora.tmail.matcher.IsMainRecipient" class="com.linagora.tmail.mailet.LlmMailPrioritizationClassifier"/>
 * }</pre>
 *
 */
public class LlmMailPrioritizationClassifier extends GenericMailet {
    private static final String NEEDS_ACTION_FLAG = "needs-action";
    private static final String SYSTEM_PROMPT_PARAMETER_NAME = "systemPrompt";
    private static final String MAX_REPORT_BODY_LENGTH_PARAMETER_NAME = "maxReportBodyLength";
    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are an email-triage classifier. Your ONLY task is to evaluate whether an incoming email requires immediate human action by the recipient.
        Return strictly one word: "YES" or "NO". Do not add explanations or any additional text.
        An email requires immediate action (YES) only if it reports urgent issues (like production failures or security incidents), time-sensitive requests or approvals (with clear deadlines), or payment/billing problems that might lead to service disruption soon.
        Return NO for newsletters, marketing, spam, general updates, casual conversations, or unclear/ambiguous messages. If the email looks like spam or phishing, always return NO.
        """;
    private static final int DEFAULT_REPORT_MAX_BODY_LENGTH = 4000;

    private final UsersRepository usersRepository;
    private final StreamingChatLanguageModel chatLanguageModel;
    private final HtmlTextExtractor htmlTextExtractor;
    private final StorageDirective addNeedsActionFlag;
    private final MetricFactory metricFactory;
    private String systemPrompt;
    private int maxReportBodyLength;

    @Inject
    public LlmMailPrioritizationClassifier(UsersRepository usersRepository,
                                           StreamingChatLanguageModel chatLanguageModel,
                                           HtmlTextExtractor htmlTextExtractor,
                                           MetricFactory metricFactory) {
        this.usersRepository = usersRepository;
        this.chatLanguageModel = chatLanguageModel;
        this.htmlTextExtractor = htmlTextExtractor;
        this.metricFactory = metricFactory;
        this.addNeedsActionFlag = StorageDirective.builder()
            .keywords(Optional.of(ImmutableList.of(NEEDS_ACTION_FLAG)))
            .build();
    }

    @Override
    public void init() {
        this.systemPrompt = Optional.ofNullable(getMailetConfig().getInitParameter(SYSTEM_PROMPT_PARAMETER_NAME))
            .orElse(DEFAULT_SYSTEM_PROMPT);

        this.maxReportBodyLength = Optional.ofNullable(getMailetConfig().getInitParameter(MAX_REPORT_BODY_LENGTH_PARAMETER_NAME))
            .map(Integer::parseInt)
            .orElse(DEFAULT_REPORT_MAX_BODY_LENGTH);
    }

    @Override
    public void service(Mail mail) throws MessagingException {
        boolean actionRequired = buildUserPrompt(mail)
            .map(userPrompt -> metricFactory.decorateSupplierWithTimerMetric(
                "llm-mail-prioritization-classifier", Throwing.supplier(() -> askLlm(systemPrompt, userPrompt))))
            .map(this::isActionRequired)
            .orElse(false);

        if (actionRequired) {
            mail.getRecipients()
                .forEach(addNeedsActionFlag(mail));
        }
    }

    private Optional<String> buildUserPrompt(Mail mail) throws MessagingException {
        try {
            MimeMessage mimeMessage = mail.getMessage();
            String from = Optional.ofNullable(mimeMessage.getHeader("From", ","))
                .map(Throwing.function(value -> MimeUtility.unfold(MimeUtility.decodeText(value))))
                .orElse("");
            String to = Optional.ofNullable(mimeMessage.getHeader("To", ","))
                .map(Throwing.function(value -> MimeUtility.unfold(MimeUtility.decodeText(value))))
                .orElse("");
            String subject = Strings.nullToEmpty(mimeMessage.getSubject());

            return extractBodyContent(mimeMessage)
                .map(bodyContent -> """
                    From: %s
                    To: %s
                    Subject: %s
            
                    %s
            
                    Does this email require immediate action? Respond only with YES or NO.
                    """
                    .formatted(from, to, subject, truncateBody(bodyContent)));
        }  catch (IOException e) {
            throw new MessagingException("Error while building LLM prompt", e);
        }
    }

    private Optional<String> extractBodyContent(MimeMessage mimeMessage) throws MessagingException, IOException {
        DefaultMessageBuilder defaultMessageBuilder = new DefaultMessageBuilder();
        defaultMessageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
        defaultMessageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
        MessageContentExtractor.MessageContent messageContent = new MessageContentExtractor()
            .extract(defaultMessageBuilder.parseMessage(new MimeMessageInputStream(mimeMessage)));

        return messageContent.extractMainTextContent(htmlTextExtractor);
    }

    private String truncateBody(String bodyContent) {
        if (bodyContent.length() <= maxReportBodyLength) {
            return bodyContent;
        }
        return bodyContent.substring(0, maxReportBodyLength) + "... [truncated]";
    }

    private String askLlm(String systemPrompt, String userPrompt) throws MessagingException {
        ChatMessage systemMessage = new SystemMessage(systemPrompt);
        ChatMessage userMessage = new UserMessage(userPrompt);
        List<ChatMessage> messages = List.of(systemMessage, userMessage);

        CompletableFuture<String> future = new CompletableFuture<>();

        chatLanguageModel.generate(messages, new StreamingResponseHandler<>() {
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
            .map(output -> output.trim().equalsIgnoreCase("YES"))
            .orElse(false);
    }

    public ThrowingConsumer<MailAddress> addNeedsActionFlag(Mail mail) {
        return recipient -> addNeedsActionFlag.encodeAsAttributes(usersRepository.getUsername(recipient))
            .forEach(mail::setAttribute);
    }
}
