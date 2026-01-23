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

package com.linagora.tmail.listener.rag;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.Identity;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.inject.name.Named;
import com.linagora.tmail.listener.rag.event.AIAnalysisNeeded;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LlmMailPrioritizationBackendClassifierListener implements EventListener.ReactiveGroupEventListener {
    public static final String LLM_MAIL_CLASSIFIER_CONFIGURATION = "llm-classifier-listener-configuration";

    public static class LlmMailPrioritizationBackendClassifierGroup extends Group {

    }

    public static final Group GROUP = new LlmMailPrioritizationBackendClassifierGroup();
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmMailPrioritizationBackendClassifierListener.class);
    private static final String NEEDS_ACTION = "needs-action";
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String MAX_BODY_LENGTH_PARAM = "maxBodyLength";
    private static final int DEFAULT_MAX_BODY_LENGTH = 4000;
    private static final int MAX_PREVIEW_LENGTH = 255;
    private static final String DEFAULT_SYSTEM_PROMPT = """
        You are an email action classifier. Your ONLY task is to decide whether the email expects the recipient to perform any action.
        
        Output: Return exactly one word: "YES" or "NO". No explanations or extra text.
        
        Return YES if the email clearly asks the recipient to do something, such as:
        - providing information, answering a question, or making a decision
        - completing a task
        - handling a problem, request, complaint, or follow-up
        
        Return NO if the email does NOT expect action, including:
        - newsletters, announcements, marketing, spam, or phishing
        - general updates, status reports, or FYI messages
        - conversations with no request (e.g., greetings, thank you messages)
        - ambiguous messages where no action is explicitly asked
        
        If the email looks like spam or phishing, always return NO.
        If the email does not target the receiving user, return NO.
        If it is unclear whether an action is expected, return NO.
        """;

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final StreamingChatLanguageModel chatLanguageModel;
    private final HtmlTextExtractor htmlTextExtractor;
    private final MetricFactory metricFactory;
    private final IdentityRepository identityRepository;
    private final String systemPrompt;
    private final int maxBodyLength;

    @Inject
    public LlmMailPrioritizationBackendClassifierListener(MailboxManager mailboxManager,
                                                          MessageIdManager messageIdManager,
                                                          StreamingChatLanguageModel chatLanguageModel,
                                                          HtmlTextExtractor htmlTextExtractor,
                                                          IdentityRepository identityRepository,
                                                          MetricFactory metricFactory,
                                                          @Named(LLM_MAIL_CLASSIFIER_CONFIGURATION) HierarchicalConfiguration<ImmutableNode> configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.chatLanguageModel = chatLanguageModel;
        this.htmlTextExtractor = htmlTextExtractor;
        this.identityRepository = identityRepository;
        this.metricFactory = metricFactory;
        this.systemPrompt = Optional.ofNullable(configuration.getString("listener.configuration." + SYSTEM_PROMPT_PARAM, null))
            .filter(s -> !s.isBlank())
            .orElse(DEFAULT_SYSTEM_PROMPT);
        this.maxBodyLength = configuration.getInt("listener.configuration." + MAX_BODY_LENGTH_PARAM, DEFAULT_MAX_BODY_LENGTH);
        Preconditions.checkArgument(maxBodyLength > 0, "'maxBodyLength' must be strictly positive");
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof AIAnalysisNeeded;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof AIAnalysisNeeded aiAnalysisNeeded) {
            return aiAnalysis(aiAnalysisNeeded);
        }

        return Mono.empty();
    }

    private Mono<Void> aiAnalysis(AIAnalysisNeeded event) {
        MailboxSession session = mailboxManager.createSystemSession(event.getUsername());

        return Flux.from(messageIdManager.getMessagesReactive(List.of(event.messageId()), FetchGroup.FULL_CONTENT, session))
            .filter(messageResult -> messageResult.getMailboxId().equals(event.mailboxId()))
            .map(LlmMailPrioritizationClassifierListener.ParsedMessage::from)
            .flatMap(messageResult -> classifyMail(messageResult, session), ReactorUtils.LOW_CONCURRENCY)
            .doFinally(signal -> mailboxManager.endProcessingRequest(session))
            .then();
    }

    private Mono<Void> classifyMail(LlmMailPrioritizationClassifierListener.ParsedMessage message, MailboxSession session) {
        return getUserDisplayName(session.getUser())
            .flatMap(userDisplayName -> Mono.fromCallable(() -> buildUserPrompt(message, session.getUser(), userDisplayName)))
            .flatMap(userPrompt -> Mono.from(metricFactory.decoratePublisherWithTimerMetric("llm-mail-prioritization-classifier",
                callLlm(systemPrompt, userPrompt.correspondingUserPrompt())))
            .flatMap(llmOutput -> {
                boolean actionRequired = isActionRequired(llmOutput);
                return emitStructureLog(userPrompt, actionRequired)
                    .thenReturn(llmOutput);
            }))
            .filter(this::isActionRequired)
            .flatMap(any -> addNeedsActionKeyword(message.messageResult(), session))
            .doOnError(e -> LOGGER.error("LLM call failed for messageId {} in mailboxId {} of user {}",
                message.messageResult().getMessageId().serialize(), message.messageResult().getMailboxId().serialize(), session.getUser(), e));
    }

    private Mono<Void> emitStructureLog(LlmUserPrompt llmUserPrompt, boolean actionRequired) {
        return Mono.fromRunnable(() -> {
            MDCStructuredLogger.forLogger(LOGGER)
                .field("sender", llmUserPrompt.sender)
                .field("user", llmUserPrompt.user)
                .field("subject", llmUserPrompt.subject)
                .field("decision", actionRequired ? "YES" : "NO")
                .field("preview", truncatePreview(llmUserPrompt.textContent()))
                .log(logger -> logger.info("Email successfully classified"));
        }).then();
    }

    private Mono<Void> addNeedsActionKeyword(MessageResult messageResult, MailboxSession session) {
        return Mono.from(messageIdManager.setFlagsReactive(new Flags(NEEDS_ACTION), MessageManager.FlagsUpdateMode.ADD,
                messageResult.getMessageId(), List.of(messageResult.getMailboxId()), session))
            .doOnSuccess(ignored -> LOGGER.info("Added '{}' keyword to message {} in mailbox {} of user {}", NEEDS_ACTION,
                messageResult.getMessageId().serialize(), messageResult.getMailboxId().serialize(), session.getUser().asString()))
            .doOnError(e -> LOGGER.error("Failed adding '{}' keyword to message {} in mailbox {} of user {}", NEEDS_ACTION,
                messageResult.getMessageId().serialize(), messageResult.getMailboxId().serialize(), session.getUser().asString(), e));
    }

    record LlmUserPrompt(String userDisplayName, String user, String textContent, String sender, String to, String subject) {
        String correspondingUserPrompt() {
            return """
                Username (of the person receiving this mail) is %s. His/her mail address is %s.
                Below is the content of the email:
                From: %s
                To: %s
                Subject: %s

                %s

                Does this email require immediate action from the user? Respond only with YES or NO.
                """.formatted(userDisplayName, user, sender, to, subject, textContent);
        }
    }

    private LlmUserPrompt buildUserPrompt(LlmMailPrioritizationClassifierListener.ParsedMessage message, Username username, String userDisplayName) throws IOException {

            String from = Optional.ofNullable(message.parsed().getFrom())
                .map(mailboxList -> mailboxList.stream()
                    .map(this::asString)
                    .collect(Collectors.joining(", ")))
                .orElse("");
            String to = Optional.ofNullable(message.parsed().getTo())
                .map(addressList -> addressList.flatten()
                    .stream()
                    .map(this::asString)
                    .collect(Collectors.joining(", ")))
                .orElse("");
            String subject = Strings.nullToEmpty(message.parsed().getSubject());
            MessageContentExtractor.MessageContent messageContent = new MessageContentExtractor().extract(message.parsed());
            Optional<String> maybeBody = messageContent.extractMainTextContent(htmlTextExtractor);

            return new LlmUserPrompt(userDisplayName, username.asString(), truncateBody(maybeBody.orElse("")), from, to, subject);
    }

    private Mono<String> getUserDisplayName(Username username) {
        return Flux.from(identityRepository.list(username))
            .filter(Identity::mayDelete)
            .sort(Comparator.comparing(Identity::sortOrder))
            .next()
            .map(Identity::name)
            .defaultIfEmpty(username.asString());
    }

    private String asString(Mailbox mime4jMailbox) {
        if (Strings.isNullOrEmpty(mime4jMailbox.getName())) {
            return String.format("%s", mime4jMailbox.getAddress());
        }
        return String.format("%s <%s>", mime4jMailbox.getName(), mime4jMailbox.getAddress());
    }

    private String truncateBody(String body) {
        if (body.length() <= maxBodyLength) {
            return body;
        }
        return body.substring(0, maxBodyLength) + "... [truncated]";
    }

    private Mono<String> callLlm(String systemPrompt, String userPrompt) {
        ChatMessage systemMessage = new SystemMessage(systemPrompt);
        ChatMessage userMessage = new UserMessage(userPrompt);
        List<ChatMessage> messages = List.of(systemMessage, userMessage);

        return Mono.create(sink -> chatLanguageModel.generate(messages, new StreamingResponseHandler<>() {
            final StringBuilder result = new StringBuilder();

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
        }));
    }

    private boolean isActionRequired(String llmOutput) {
        return Optional.ofNullable(llmOutput)
            .map(String::trim)
            .map(s -> s.equalsIgnoreCase("YES"))
            .orElse(false);
    }

    private String truncatePreview(String body) {
        if (body.length() <= MAX_PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_PREVIEW_LENGTH);
    }
}
