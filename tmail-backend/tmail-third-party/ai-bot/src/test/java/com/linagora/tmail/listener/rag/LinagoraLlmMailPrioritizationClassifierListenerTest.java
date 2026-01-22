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

import static com.linagora.tmail.listener.rag.MockLlmMailPrioritizationClassifierListenerTest.setUpIdentityRepository;
import static com.linagora.tmail.mailet.AIBotConfig.DEFAULT_TIMEOUT;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.html.HtmlTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.linagora.tmail.listener.rag.logger.NeedsActionReviewLogger;
import com.linagora.tmail.mailet.AIBotConfig;
import com.linagora.tmail.mailet.LlmModel;
import com.linagora.tmail.mailet.StreamChatLanguageModelFactory;

import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import reactor.core.publisher.Mono;

@Disabled("Manual run. Requires a valid Linagora AI's API key to be run")
public class LinagoraLlmMailPrioritizationClassifierListenerTest implements LlmMailPrioritizationClassifierListenerContract {

    private MessageIdManager messageIdManager;
    private MailboxSession aliceSession;
    private MessageManager aliceInbox;
    private MessageManager aliceSpam;
    private MessageManager aliceCustomMailbox;
    private HierarchicalConfiguration<ImmutableNode> listenerConfig;
    private StoreMailboxManager mailboxManager;
    private StreamingChatLanguageModel chatLanguageModel;
    private IdentityRepository identityRepository;
    private JmapSettingsRepository jmapSettingsRepository;
    private JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils;
    private LlmMailPrioritizationClassifierListener listener;
    private LlmMailPrioritizationBackendClassifierListener backendListener;
    private EventBus tmailEventBus;

    @BeforeEach
    void setup() throws Exception {
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();

        MemoryEventDeadLetters eventDeadLetters = new MemoryEventDeadLetters();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(new InVmEventDelivery(new RecordingMetricFactory()), backoffConfiguration, eventDeadLetters))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();
        mailboxManager = resources.getMailboxManager();
        messageIdManager = resources.getMessageIdManager();
        MetricFactory metricFactory = new RecordingMetricFactory();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();
        NeedsActionReviewLogger needsActionReviewLogger = new NeedsActionReviewLogger();
        tmailEventBus = new InVMEventBus(new InVmEventDelivery(metricFactory), backoffConfiguration, eventDeadLetters);

        aliceSession = mailboxManager.createSystemSession(ALICE);
        MailboxPath aliceInboxPath = MailboxPath.inbox(ALICE);
        MailboxPath spamMailboxPath = MailboxPath.forUser(ALICE, "Spam");
        mailboxManager.createMailbox(aliceInboxPath, aliceSession);
        mailboxManager.createMailbox(spamMailboxPath, aliceSession);
        aliceInbox = mailboxManager.getMailbox(aliceInboxPath, aliceSession);
        aliceSpam = mailboxManager.getMailbox(spamMailboxPath, aliceSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "customMailbox"), aliceSession);
        aliceCustomMailbox = mailboxManager.getMailbox(MailboxPath.forUser(ALICE, "customMailbox"), aliceSession);

        listenerConfig = new BaseHierarchicalConfiguration();

        AIBotConfig aiBotConfig = new AIBotConfig(
            Optional.ofNullable(System.getenv("LLM_API_KEY")).orElse("change-me"),
            new LlmModel("openai/gpt-oss-120b"),
            Optional.of(URI.create("https://ai.linagora.com/api/v1/").toURL()),
            DEFAULT_TIMEOUT);
        StreamChatLanguageModelFactory streamChatLanguageModelFactory = new StreamChatLanguageModelFactory();
        chatLanguageModel = streamChatLanguageModelFactory.createChatLanguageModel(aiBotConfig);
        identityRepository = setUpIdentityRepository();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);

        listener = new LlmMailPrioritizationClassifierListener(
            mailboxManager,
            messageIdManager,
            new SystemMailboxesProviderImpl(mailboxManager),
            jmapSettingsRepository,
            tmailEventBus,
            listenerConfig);
        backendListener = new LlmMailPrioritizationBackendClassifierListener(
            mailboxManager,
            messageIdManager,
            chatLanguageModel,
            htmlTextExtractor,
            identityRepository,
            metricFactory,
            listenerConfig,
            needsActionReviewLogger);

        jmapSettingsRepositoryUtils().reset(ALICE, ImmutableMap.of("ai.needs-action.enabled", "true"));
    }

    @Override
    public MessageManager aliceInbox() {
        return aliceInbox;
    }

    @Override
    public MessageManager aliceSpam() {
        return aliceSpam;
    }

    @Override
    public MessageManager aliceCustomMailbox() {
        return aliceCustomMailbox;
    }

    @Override
    public MailboxSession aliceSession() {
        return aliceSession;
    }

    @Override
    public HierarchicalConfiguration<ImmutableNode> listenerConfig() {
        return listenerConfig;
    }

    @Override
    public void resetListenerWithConfig(HierarchicalConfiguration<ImmutableNode> overrideConfig) {
        listener = new LlmMailPrioritizationClassifierListener(
            mailboxManager,
            messageIdManager,
            new SystemMailboxesProviderImpl(mailboxManager),
            jmapSettingsRepository,
            tmailEventBus,
            overrideConfig);
        backendListener = new LlmMailPrioritizationBackendClassifierListener(
            mailboxManager,
            messageIdManager,
            chatLanguageModel,
            new JsoupHtmlTextExtractor(),
            identityRepository,
            new RecordingMetricFactory(),
            overrideConfig,
            new NeedsActionReviewLogger());
    }

    @Override
    public void registerListenerToEventBus() {
        mailboxManager.getEventBus().register(listener);
        tmailEventBus.register(backendListener);
    }

    @Override
    public JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils() {
        return jmapSettingsRepositoryUtils;
    }

    @Override
    public Mono<Flags> readFlags(MessageId messageId, MailboxSession userSession) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, userSession))
            .map(MessageResult::getFlags);
    }
}
