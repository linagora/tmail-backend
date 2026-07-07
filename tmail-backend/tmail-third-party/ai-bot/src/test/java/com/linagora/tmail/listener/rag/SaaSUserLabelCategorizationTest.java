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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.events.EventBus;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.EmailAddress;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.AliasReverseResolverImpl;
import org.apache.james.rrt.lib.CanSendFromImpl;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
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
import org.apache.james.mime4j.dom.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.james.jmap.event.ApplyWhenFilter;
import com.linagora.tmail.james.jmap.event.IdentityCreationRequestBuilder;
import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.label.MemoryLabelRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;
import com.linagora.tmail.listener.rag.prompt.DefaultPromptRetrieverFactory;
import com.linagora.tmail.listener.rag.prompt.PromptRetriever;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.filter.SaaSPayingUser;
import com.linagora.tmail.saas.model.SaaSAccount;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;

import reactor.core.publisher.Mono;

class SaaSUserLabelCategorizationTest {
    static final String NEEDS_ACTION_FLAG = LlmMailBackendClassifierListener.NEEDS_ACTION;
    static final Username ALICE = Username.of("alice@example.com");
    static final Username BOB = Username.of("bob@example.com");

    static class StubModel implements StreamingChatLanguageModel {
        volatile String llOutput;

        @Override
        public void generate(List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            handler.onNext(llOutput);
            handler.onComplete(Response.from(AiMessage.from(llOutput)));
        }
    }

    StoreMailboxManager mailboxManager;
    MailboxSession aliceSession;
    MessageManager aliceInbox;
    StubModel model;
    HierarchicalConfiguration<ImmutableNode> listenerConfig;
    JmapSettingsRepository jmapSettingsRepository;
    LabelRepository labelRepository;
    PromptRetriever.Factory promptRetrieverFactory;
    EventBus tmailEventBus;
    MemorySaaSAccountRepository saasAccountRepository;

    @BeforeEach
    void setup() throws Exception {
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();

        MemoryEventDeadLetters eventDeadLetters = new MemoryEventDeadLetters();
        MetricFactory metricFactory = new RecordingMetricFactory();

        model = new StubModel();
        tmailEventBus = new InVMEventBus(new InVmEventDelivery(metricFactory), backoffConfiguration, eventDeadLetters);
        listenerConfig = new BaseHierarchicalConfiguration();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);
        labelRepository = new MemoryLabelRepository(tmailEventBus);
        promptRetrieverFactory = new DefaultPromptRetrieverFactory();
        saasAccountRepository = new MemorySaaSAccountRepository();

        jmapSettingsRepositoryUtils.reset(ALICE, ImmutableMap.of("ai.label-categorization.enabled", "true"));
    }

    void setUpListeners(ApplyWhenFilter applyWhenFilter, MessageIdManager messageIdManager, IdentityRepository identityRepository) {
        LlmMailClassifierListener listener = new LlmMailClassifierListener(
            mailboxManager,
            messageIdManager,
            new SystemMailboxesProviderImpl(mailboxManager),
            jmapSettingsRepository,
            tmailEventBus,
            listenerConfig,
            applyWhenFilter);
        LlmMailBackendClassifierListener backendListener = new LlmMailBackendClassifierListener(
            mailboxManager,
            messageIdManager,
            model,
            new JsoupHtmlTextExtractor(),
            identityRepository,
            new RecordingMetricFactory(),
            labelRepository,
            listenerConfig,
            promptRetrieverFactory);

        mailboxManager.getEventBus().register(listener);
        tmailEventBus.register(backendListener);
    }

    MessageId appendUrgentEmail(MessageManager inbox, MailboxSession session) throws Exception {
        return inbox.appendMessage(MessageManager.AppendCommand.builder()
                .isDelivery(true)
                .build(Message.Builder.of()
                    .setSubject("URGENT – Production API Failure")
                    .setMessageId("Message-ID")
                    .setFrom(BOB.asString())
                    .setTo(ALICE.asString())
                    .setBody("""
                        Hi team,
                        Our payment gateway API has been failing since 03:12 AM UTC.
                        Please acknowledge as soon as possible.
                        Thanks,
                        Robert
                        """, StandardCharsets.UTF_8)),
            session)
            .getId().getMessageId();
    }

    Mono<Flags> readFlags(MessageIdManager messageIdManager, MessageId messageId, MailboxSession session) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags);
    }


    @Test
    void payingUserShouldTriggerAIEventWithSaaSPayingUserFilter() throws Exception {
        MessageIdManager messageIdManager = setUpPayingUserTest(new SaaSAccount(true, true));

        MessageId messageId = appendUrgentEmail(aliceInbox, aliceSession);

        awaitUntilAsserted(() -> {
            assertThat(readFlags(messageIdManager, messageId, aliceSession).block()).isNotNull();
            assertThat(readFlags(messageIdManager, messageId, aliceSession).block().getUserFlags())
                .contains(NEEDS_ACTION_FLAG);
        });
    }

    @Test
    void nonPayingUserShouldNotTriggerAIEventWithSaaSPayingUserFilter() throws Exception {
        MessageIdManager messageIdManager = setUpPayingUserTest(new SaaSAccount(true, false));

        MessageId messageId = appendUrgentEmail(aliceInbox, aliceSession);

        awaitUntilAsserted(() -> {
            assertThat(readFlags(messageIdManager, messageId, aliceSession).block()).isNotNull();
            assertThat(readFlags(messageIdManager, messageId, aliceSession).block().getUserFlags())
                .doesNotContain(NEEDS_ACTION_FLAG);
        });
    }

    @Test
    void noSaaSAccountStoredShouldNotTriggerAIEventWithSaaSPayingUserFilter() throws Exception {
        MessageIdManager messageIdManager = setUpPayingUserTest(null);

        MessageId messageId = appendUrgentEmail(aliceInbox, aliceSession);

        awaitUntilAsserted(() -> {
            assertThat(readFlags(messageIdManager, messageId, aliceSession).block()).isNotNull();
            assertThat(readFlags(messageIdManager, messageId, aliceSession).block().getUserFlags())
                .doesNotContain(NEEDS_ACTION_FLAG);
        });
    }

    private MessageIdManager setUpPayingUserTest(SaaSAccount account) throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .eventBus(new InVMEventBus(
                new InVmEventDelivery(new RecordingMetricFactory()),
                RetryBackoffConfiguration.builder()
                    .maxRetries(2)
                    .firstBackoff(Duration.ofMillis(1))
                    .jitterFactor(0.5)
                    .build(),
                new MemoryEventDeadLetters()))
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        mailboxManager = resources.getMailboxManager();
        MessageIdManager messageIdManager = resources.getMessageIdManager();

        aliceSession = mailboxManager.createSystemSession(ALICE);

        MailboxPath aliceInboxPath = MailboxPath.inbox(ALICE);
        mailboxManager.createMailbox(aliceInboxPath, aliceSession);
        aliceInbox = mailboxManager.getMailbox(aliceInboxPath, aliceSession);

        IdentityRepository identityRepository = setUpIdentityRepository(tmailEventBus);

        setUpListeners(
            new SaaSPayingUser(saasAccountRepository),
            messageIdManager,
            identityRepository
        );

        if (account != null) {
            Mono.from(
                saasAccountRepository.upsertSaasAccount(ALICE, account)
            ).block();
        }

        model.llOutput = "%s"
            .formatted(NEEDS_ACTION_FLAG);

        return messageIdManager;
    }

    private static void awaitUntilAsserted(Runnable assertion) {
        org.awaitility.Awaitility.with()
            .pollInterval(ONE_HUNDRED_MILLISECONDS)
            .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
            .await()
            .atMost(org.awaitility.Durations.TEN_SECONDS)
            .untilAsserted(assertion::run);
    }

    static IdentityRepository setUpIdentityRepository(EventBus eventBus) throws Exception {
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        MemoryRecipientRewriteTable memoryRecipientRewriteTable = new MemoryRecipientRewriteTable();
        memoryRecipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        CanSendFromImpl canSendFrom = new CanSendFromImpl(new AliasReverseResolverImpl(memoryRecipientRewriteTable));
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        DefaultIdentitySupplier identityFactory = new DefaultIdentitySupplier(canSendFrom, usersRepository);
        IdentityRepository identityRepository = new IdentityRepository(new MemoryCustomIdentityDAO(eventBus), identityFactory);

        Integer highPriorityOrder = 1;
        Integer lowPriorityOrder = 2;

        IdentityCreationRequest creationRequest1 = IdentityCreationRequestBuilder.builder()
            .email(ALICE.asMailAddress())
            .name("Alice in wonderland")
            .replyTo(List.of(EmailAddress.from(Optional.of("reply name 1"), new MailAddress("reply1@domain.org"))))
            .bcc(List.of(EmailAddress.from(Optional.of("bcc name 1"), new MailAddress("bcc1@domain.org"))))
            .sortOrder(highPriorityOrder)
            .textSignature("textSignature 1")
            .htmlSignature("htmlSignature 1")
            .build();

        IdentityCreationRequest creationRequest2 = IdentityCreationRequestBuilder.builder()
            .email(ALICE.asMailAddress())
            .name("Alice in borderland")
            .replyTo(List.of(EmailAddress.from(Optional.of("reply name 2"), new MailAddress("reply2@domain.org"))))
            .bcc(List.of(EmailAddress.from(Optional.of("bcc name 2"), new MailAddress("bcc2@domain.org"))))
            .sortOrder(lowPriorityOrder)
            .textSignature("textSignature 2")
            .htmlSignature("htmlSignature 2")
            .build();

        Mono.from(identityRepository.save(ALICE, creationRequest1)).block();
        Mono.from(identityRepository.save(ALICE, creationRequest2)).block();

        return identityRepository;
    }
}
