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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.inject.name.Named;
import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelId;
import com.linagora.tmail.listener.rag.event.AIAnalysisNeeded;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class LlmMailBackendClassifierListener implements EventListener.ReactiveGroupEventListener {
    public static final String LLM_MAIL_CLASSIFIER_CONFIGURATION = "llm-classifier-listener-configuration";

    public static class LlmMailPrioritizationBackendClassifierGroup extends Group {

    }

    public static final Group GROUP = new LlmMailPrioritizationBackendClassifierGroup();
    public static final String NEEDS_ACTION = "needs-action";
    public static ImmutableMap<String, Label> SYSTEM_LABELS = ImmutableMap.of(NEEDS_ACTION, new Label(
        LabelId.fromKeyword(NEEDS_ACTION),
        DisplayName.apply(NEEDS_ACTION),
        NEEDS_ACTION,
        scala.Option.apply(new Color("#FF0000")),
        scala.Option.apply("Emails requiring recipient action: answering questions, making decisions, completing tasks, handling requests, or responding by deadline. Excludes newsletters, notifications, and FYI messages.")));
    private static final Logger LOGGER = LoggerFactory.getLogger(LlmMailBackendClassifierListener.class);
    private static final String SYSTEM_PROMPT_PARAM = "systemPrompt";
    private static final String MAX_BODY_LENGTH_PARAM = "maxBodyLength";
    private static final int DEFAULT_MAX_BODY_LENGTH = 4000;
    private static final int MAX_PREVIEW_LENGTH = 255;
    private static final String DUAL_LABELING_PROPERTY = "tmail.ai.label.relevance.audit.track";
    private static final Boolean DUAL_LABELING_ENABLED = Boolean.parseBoolean(System.getProperty(DUAL_LABELING_PROPERTY, "false"));
    public static final String GUESSED_LABEL_SUFFIX = "-save";
    private static final String DEFAULT_SYSTEM_PROMPT = """
    Analyze the email and select labels that best match its content and intent.
    
    Selection criteria:
    - Choose labels whose descriptions match the email's topic, intent, or category
    - Prioritize specificity: prefer specific labels over generic ones
    - Only include genuinely relevant labels
    - You may return labels depending on relevance
    
    OUTPUT FORMAT:
    Return label IDs as comma-separated values with no spaces.
    
    Examples:
    - needs-action,label_work
    - label_personal
    - needs-action
    - (empty if no labels match)
    
    Return ONLY the label IDs. No explanations.
    """;

    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final StreamingChatLanguageModel chatLanguageModel;
    private final HtmlTextExtractor htmlTextExtractor;
    private final MetricFactory metricFactory;
    private final IdentityRepository identityRepository;
    private final String systemPrompt;
    private final int maxBodyLength;
    private final LabelRepository labelRepository;
    private final boolean reviewModeEnabled;

    @Inject
    public LlmMailBackendClassifierListener(MailboxManager mailboxManager,
                                            MessageIdManager messageIdManager,
                                            StreamingChatLanguageModel chatLanguageModel,
                                            HtmlTextExtractor htmlTextExtractor,
                                            IdentityRepository identityRepository,
                                            MetricFactory metricFactory,
                                            LabelRepository labelRepository,
                                            @Named(LLM_MAIL_CLASSIFIER_CONFIGURATION) HierarchicalConfiguration<ImmutableNode> configuration) {
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.chatLanguageModel = chatLanguageModel;
        this.htmlTextExtractor = htmlTextExtractor;
        this.identityRepository = identityRepository;
        this.metricFactory = metricFactory;
        this.systemPrompt = Optional.ofNullable(configuration.getString(SYSTEM_PROMPT_PARAM, null))
            .filter(s -> !s.isBlank())
            .orElse(DEFAULT_SYSTEM_PROMPT);
        this.labelRepository = labelRepository;
        this.maxBodyLength = configuration.getInt(MAX_BODY_LENGTH_PARAM, DEFAULT_MAX_BODY_LENGTH);
        Preconditions.checkArgument(maxBodyLength > 0, "'maxBodyLength' must be strictly positive");
        this.reviewModeEnabled = Boolean.parseBoolean(System.getProperty("tmail.ai.needsaction.relevance.review", "false"));
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
            .map(LlmMailClassifierListener.ParsedMessage::from)
            .flatMap(messageResult -> classifyMail(messageResult, session), ReactorUtils.LOW_CONCURRENCY)
            .doFinally(signal -> mailboxManager.endProcessingRequest(session))
            .then();
    }

    record LlmOutput(ImmutableSet<String> labels) {
        static LlmOutput parse(String llmOutput) {
            return new LlmOutput(Splitter.on(',')
                .omitEmptyStrings()
                .trimResults()
                .splitToStream(llmOutput)
                .collect(ImmutableSet.toImmutableSet()));
        }

        Set<String> validateLabelIds(Set<Label> userLabels) {
            Set<String> allAvailableLabels = Stream.concat(userLabels.stream(), SYSTEM_LABELS.values().stream())
                .map(Label::keyword)
                .collect(Collectors.toSet());

            return Sets.intersection(allAvailableLabels, labels);
        }

        Optional<Flags> flagsToSet(UserContext context) {
            if (isEmpty()) {
                return Optional.empty();
            }

            Flags flags = new Flags();
            validateLabelIds(context.labels()).forEach(label -> {
                flags.add(label);
                if (DUAL_LABELING_ENABLED) {
                    flags.add(label + GUESSED_LABEL_SUFFIX);
                }
            });

            return Optional.of(flags);
        }

        boolean isEmpty() {
            return labels.isEmpty();
        }
    }

    record UserContext(String displayName, Set<Label> labels) {
    }

    private Mono<Void> classifyMail(LlmMailClassifierListener.ParsedMessage message, MailboxSession session) {
        return Mono.zip(getUserDisplayName(session.getUser()), getUserLabels(session.getUser()), UserContext::new)
            .flatMap(userContext -> Mono.fromCallable(() -> buildUserPrompt(message, session.getUser(), userContext))
                .flatMap(userPrompt -> performLlmClassification(message, session, userPrompt, userContext)))
            .doOnError(e -> LOGGER.error("LLM call failed for messageId {} in mailboxId {} of user {}",
                message.messageResult().getMessageId().serialize(), message.messageResult().getMailboxId().serialize(), session.getUser(), e));
    }

    private Mono<Void> performLlmClassification(LlmMailClassifierListener.ParsedMessage message, MailboxSession session, LlmUserPrompt userPrompt, UserContext userContext) {
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("llm-mail-prioritization-classifier",
                callLlm(systemPrompt, userPrompt.correspondingUserPrompt())))
            .doOnNext(llmOutput -> emitStructureLog(userPrompt, llmOutput, userContext))
            .flatMap(llmOutput -> addFlags(message.messageResult(), session, llmOutput.flagsToSet(userContext)));
    }

    private void emitStructureLog(LlmUserPrompt llmUserPrompt, LlmOutput llmOutput, UserContext userContext) {
            if (reviewModeEnabled) {
                MDCStructuredLogger.forLogger(LOGGER)
                    .field("sender", llmUserPrompt.sender())
                    .field("user", llmUserPrompt.user())
                    .field("subject", llmUserPrompt.subject())
                    .field("decision", llmOutput.labels.contains(SYSTEM_LABELS.get(NEEDS_ACTION).keyword()) ? "YES" : "NO")
                    .field("labels suggested", getLabelsNamesFromIds(llmOutput.labels, userContext.labels()))
                    .field("preview", truncatePreview(llmUserPrompt.textContent()))
                    .log(logger -> logger.info("Email successfully classified"));
            }
    }

    private String getLabelsNamesFromIds(Set<String> labelIds, Set<Label> userLabels) {
        Set<String> guessedLabelNames = Stream.concat(userLabels.stream(), SYSTEM_LABELS.values().stream())
            .filter(label -> labelIds.contains(label.keyword()))
            .map(Label::displayName)
            .map(DisplayName::value)
            .collect(Collectors.toSet());
        return String.join(", ", guessedLabelNames);
    }

    private Mono<Void> addFlags(MessageResult messageResult, MailboxSession session, Optional<Flags> flags) {
        return flags.map(actualFlags ->
            Mono.from(messageIdManager.setFlagsReactive(actualFlags, MessageManager.FlagsUpdateMode.ADD,
                    messageResult.getMessageId(), List.of(messageResult.getMailboxId()), session)))
            .orElseGet(Mono::empty);
    }

    private Mono<Set<Label>> getUserLabels(Username username) {
        return Flux.from(labelRepository.listLabels(username))
            .collect(Collectors.toSet())
            .doOnNext(labels -> LOGGER.debug("Retrieved {} labels for user {}", labels.size(), username.asString()));
    }

    record LlmUserPrompt(String userDisplayName, String user, String textContent, String sender, String to, String subject, String labelsInfo) {
        String correspondingUserPrompt() {
            return """ 
                Username (of the person receiving this mail) is %s. His/her mail address is %s.
                Below is the content of the email:
                        
                From: %s
                To: %s
                Subject: %s
                 
                Body:
                %s
                        
                ## AVAILABLE LABELS
                %s

               Classify this email and assign relevant labels.
                """.formatted(userDisplayName, user, sender, to, subject, textContent, labelsInfo);
        }
    }

    private LlmUserPrompt buildUserPrompt(LlmMailClassifierListener.ParsedMessage message, Username username, UserContext userContext) throws IOException {

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
            String labelsInfo = buildLabelsInfo(userContext.labels());
            MessageContentExtractor.MessageContent messageContent = new MessageContentExtractor().extract(message.parsed());
            Optional<String> maybeBody = messageContent.extractMainTextContent(htmlTextExtractor);

            return new LlmUserPrompt(userContext.displayName(), username.asString(), truncateBody(maybeBody.orElse("")), from, to, subject, labelsInfo);
    }

    private String buildLabelsInfo(Set<Label> userLabels) {
        return  Stream.concat(SYSTEM_LABELS.values().stream(), userLabels.stream())
            .map(label -> "labelId : " + label.keyword() + " - Label name :" + label.displayName() + " - label description :" + OptionConverters.toJava(label.description()).orElse("No description"))
            .collect(Collectors.joining("\n- ", "- ", ""));
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

    private Mono<LlmOutput> callLlm(String systemPrompt, String userPrompt) {
        ChatMessage systemMessage = new SystemMessage(systemPrompt);
        ChatMessage userMessage = new UserMessage(userPrompt);
        List<ChatMessage> messages = List.of(systemMessage, userMessage);

        return Mono.create(sink -> chatLanguageModel.generate(messages, new StreamingResponseHandler<>() {
            final StringBuilder result = new StringBuilder();

            @Override
            public void onComplete(Response response) {
                sink.success(LlmOutput.parse(result.toString()));
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

    private String truncatePreview(String body) {
        if (body.length() <= MAX_PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, MAX_PREVIEW_LENGTH);
    }
}
