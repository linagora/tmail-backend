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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;

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
import org.apache.james.mailbox.Role;
import org.apache.james.mailbox.SystemMailboxesProvider;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.mime4j.codec.DecodeMonitor;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.message.DefaultMessageBuilder;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.linagora.tmail.james.jmap.settings.JmapSettings;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class LlmMailPrioritizationClassifierListener implements EventListener.ReactiveGroupEventListener {
    public static class LlmMailPrioritizationClassifierGroup extends Group {

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(LlmMailPrioritizationClassifierListener.class);
    private static final Group GROUP = new LlmMailPrioritizationClassifierGroup();

    private static final String NEEDS_ACTION = "needs-action";
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String MAX_BODY_LENGTH_PARAM = "maxBodyLength";
    private static final int DEFAULT_MAX_BODY_LENGTH = 4000;
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
    private final SystemMailboxesProvider systemMailboxesProvider;
    private final StreamingChatLanguageModel chatLanguageModel;
    private final HtmlTextExtractor htmlTextExtractor;
    private final MetricFactory metricFactory;
    private final IdentityRepository identityRepository;
    private final JmapSettingsRepository jmapSettingsRepository;
    private final String systemPrompt;
    private final int maxBodyLength;

    @Inject
    public LlmMailPrioritizationClassifierListener(MailboxManager mailboxManager,
                                                   MessageIdManager messageIdManager,
                                                   SystemMailboxesProvider systemMailboxesProvider,
                                                   StreamingChatLanguageModel chatLanguageModel,
                                                   HtmlTextExtractor htmlTextExtractor,
                                                   IdentityRepository identityRepository,
                                                   JmapSettingsRepository jmapSettingsRepository,
                                                   MetricFactory metricFactory,
                                                   HierarchicalConfiguration<ImmutableNode> configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.systemMailboxesProvider = systemMailboxesProvider;
        this.chatLanguageModel = chatLanguageModel;
        this.htmlTextExtractor = htmlTextExtractor;
        this.identityRepository = identityRepository;
        this.jmapSettingsRepository = jmapSettingsRepository;
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
        return event instanceof MailboxEvents.Added added && added.isDelivery();
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof MailboxEvents.Added added && added.isDelivery()) {
            return isAppendedToInbox(added)
                .filter(Boolean::booleanValue)
                .flatMap(matched -> processAddedEvent(added))
                .then();
        }

        return Mono.empty();
    }

    private Mono<Boolean> isAppendedToInbox(MailboxEvents.Added addedEvent) {
        return Flux.from(systemMailboxesProvider.getMailboxByRole(Role.INBOX, addedEvent.getUsername()))
            .map(MessageManager::getId)
            .any(inboxId -> inboxId.equals(addedEvent.getMailboxId()));
    }

    private Mono<Void> processAddedEvent(MailboxEvents.Added addedEvent) {
        Username username = addedEvent.getUsername();
        MailboxSession session = mailboxManager.createSystemSession(username);

        return aiNeedActionsSettingEnabled(username)
            .flatMap(needsActionEnabled -> classifyMailsPrioritization(addedEvent, username, session));
    }

    private Mono<Void> classifyMailsPrioritization(MailboxEvents.Added addedEvent, Username username, MailboxSession session) {
        return getUserDisplayName(username)
            .flatMap(userDisplayName -> classifyMails(addedEvent, username, session, userDisplayName));
    }

    private Mono<Void> classifyMails(MailboxEvents.Added addedEvent, Username username, MailboxSession session, String userDisplayName) {
        return getMessages(addedEvent, session)
            .flatMap(messageResult -> classifyMail(addedEvent, username, session, userDisplayName, messageResult), ReactorUtils.LOW_CONCURRENCY)
            .doFinally(signal -> mailboxManager.endProcessingRequest(session))
            .then();
    }

    private Flux<MessageResult> getMessages(MailboxEvents.Added addedEvent, MailboxSession session) {
        return Flux.from(messageIdManager.getMessagesReactive(addedEvent.getMessageIds(), FetchGroup.FULL_CONTENT, session));
    }

    private Mono<Void> classifyMail(MailboxEvents.Added addedEvent, Username username, MailboxSession session, String userDisplayName, MessageResult messageResult) {
        return buildUserPrompt(messageResult, username, userDisplayName)
            .flatMap(userPrompt -> callLlm(addedEvent, messageResult, userPrompt))
            .map(this::isActionRequired)
            .flatMap(actionRequired -> actionRequired ? addNeedsActionKeyword(messageResult, addedEvent.getMailboxId(), session) : Mono.empty());
    }

    private Mono<String> callLlm(MailboxEvents.Added addedEvent, MessageResult messageResult, String userPrompt) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("llm-mail-prioritization-classifier", callLlm(systemPrompt, userPrompt)))
            .doOnError(e -> LOGGER.error("LLM call failed for messageId {} in mailboxId {} of user {}",
                messageResult.getMessageId().serialize(), addedEvent.getMailboxId().serialize(), addedEvent.getUsername().asString(), e));
    }

    private Mono<Boolean> aiNeedActionsSettingEnabled(Username username) {
        return Mono.from(jmapSettingsRepository.get(username))
            .map(JmapSettings::aiNeedsActionEnable)
            .defaultIfEmpty(JmapSettings.AI_NEEDS_ACTION_ENABLE_DEFAULT_VALUE())
            .filter(Boolean::booleanValue);
    }

    private Mono<Void> addNeedsActionKeyword(MessageResult messageResult, MailboxId mailboxId, MailboxSession session) {
        return Mono.from(messageIdManager.setFlagsReactive(new Flags(NEEDS_ACTION), MessageManager.FlagsUpdateMode.ADD, messageResult.getMessageId(), List.of(mailboxId), session))
            .doOnSuccess(ignored -> LOGGER.info("Added '{}' keyword to message {} in mailbox {} of user {}", NEEDS_ACTION, messageResult.getMessageId().serialize(), mailboxId.serialize(), session.getUser().asString()))
            .doOnError(e -> LOGGER.error("Failed adding '{}' keyword to message {} in mailbox {} of user {}", NEEDS_ACTION, messageResult.getMessageId().serialize(), mailboxId.serialize(), session.getUser().asString(), e));
    }

    private Mono<String> buildUserPrompt(MessageResult messageResult, Username username, String userDisplayName) {
        try {
            DefaultMessageBuilder messageBuilder = new DefaultMessageBuilder();
            messageBuilder.setMimeEntityConfig(MimeConfig.PERMISSIVE);
            messageBuilder.setDecodeMonitor(DecodeMonitor.SILENT);
            Message mimeMessage = messageBuilder.parseMessage(messageResult.getFullContent().getInputStream());

            String from = Optional.ofNullable(mimeMessage.getFrom())
                .map(mailboxList -> mailboxList.stream()
                    .map(this::asString)
                    .collect(Collectors.joining(", ")))
                .orElse("");
            String to = Optional.ofNullable(mimeMessage.getTo())
                .map(addressList -> addressList.flatten()
                    .stream()
                    .map(this::asString)
                    .collect(Collectors.joining(", ")))
                .orElse("");
            String subject = Strings.nullToEmpty(mimeMessage.getSubject());
            MessageContentExtractor.MessageContent messageContent = new MessageContentExtractor().extract(mimeMessage);
            Optional<String> maybeBody = messageContent.extractMainTextContent(htmlTextExtractor);

            return Mono.justOrEmpty(maybeBody)
                .map(body -> {
                    String truncated = truncate(body);
                    return """
                        Username (of the person receiving this mail) is %s. His/her mail address is %s.
                        Below is the content of the email:
                        
                        From: %s
                        To: %s
                        Subject: %s

                        %s

                        Does this email require immediate action from the user? Respond only with YES or NO.
                        """.formatted(userDisplayName, username.asString(), from, to, subject, truncated);
                });
        } catch (Exception e) {
            return Mono.error(new MessagingException("Error while building LLM prompt", e));
        }
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

    private String truncate(String body) {
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
}
