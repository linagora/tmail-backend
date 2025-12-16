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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;
import jakarta.mail.Flags;
import jakarta.mail.internet.AddressException;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.core.MailAddress;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.jmap.api.identity.DefaultIdentitySupplier;
import org.apache.james.jmap.api.identity.IdentityCreationRequest;
import org.apache.james.jmap.api.identity.IdentityRepository;
import org.apache.james.jmap.api.model.EmailAddress;
import org.apache.james.jmap.memory.identity.MemoryCustomIdentityDAO;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.util.html.HtmlTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepository;
import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scala.publisher.SMono;

public class MockLlmMailPrioritizationClassifierListenerTest implements LlmMailPrioritizationClassifierListenerContract {

    static class StubModel implements StreamingChatLanguageModel {
        volatile String llOutput;

        @Override
        public void generate(java.util.List<ChatMessage> messages, StreamingResponseHandler<AiMessage> handler) {
            handler.onNext(llOutput);
            handler.onComplete(Response.from(AiMessage.from(llOutput)));
        }
    }

    private MessageIdManager messageIdManager;
    private StubModel model;
    private MailboxSession aliceSession;
    private MessageManager aliceInbox;
    private MessageManager aliceSpam;
    private MessageManager aliceCustomMailbox;
    private HierarchicalConfiguration<ImmutableNode> listenerConfig;
    private StoreMailboxManager mailboxManager;
    private IdentityRepository identityRepository;
    private JmapSettingsRepository jmapSettingsRepository;
    private JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils;
    private LlmMailPrioritizationClassifierListener listener;

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
        model = new StubModel();

        aliceSession = mailboxManager.createSystemSession(ALICE);
        MailboxPath spamMailboxPath = MailboxPath.forUser(ALICE, "Spam");
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "customMailbox"), aliceSession).get();
        aliceCustomMailbox = mailboxManager.getMailbox(MailboxPath.forUser(ALICE, "customMailbox"), aliceSession);

        listenerConfig = new BaseHierarchicalConfiguration();
        identityRepository = setUpIdentityRepository();
        jmapSettingsRepository = new MemoryJmapSettingsRepository();
        jmapSettingsRepositoryUtils = new JmapSettingsRepositoryJavaUtils(jmapSettingsRepository);

        listener = new LlmMailPrioritizationClassifierListener(
            mailboxManager,
            messageIdManager,
            new SystemMailboxesProviderImpl(mailboxManager),
            model,
            htmlTextExtractor,
            identityRepository,
            jmapSettingsRepository,
            metricFactory,
            listenerConfig);

        jmapSettingsRepositoryUtils().reset(ALICE, ImmutableMap.of("ai.needs-action.enabled", "true"));
    }

    public static IdentityRepository setUpIdentityRepository() throws AddressException {
        DefaultIdentitySupplier identityFactory = mock(DefaultIdentitySupplier.class);
        Mockito.when(identityFactory.listIdentities(ALICE))
            .thenReturn(Flux.just(IdentityFixture.ALICE_SERVER_SET_IDENTITY()));
        Mockito.when(identityFactory.userCanSendFrom(any(), any()))
            .thenReturn(SMono.just(true));
        IdentityRepository identityRepository = new IdentityRepository(new MemoryCustomIdentityDAO(), identityFactory);

        Integer highPriorityOrder = 1;
        Integer lowPriorityOrder = 2;
        IdentityCreationRequest creationRequest1 = IdentityCreationRequest.fromJava(
            ALICE.asMailAddress(),
            Optional.of("Alice in wonderland"),
            Optional.of(List.of(EmailAddress.from(Optional.of("reply name 1"), new MailAddress("reply1@domain.org")))),
            Optional.of(List.of(EmailAddress.from(Optional.of("bcc name 1"), new MailAddress("bcc1@domain.org")))),
            Optional.of(highPriorityOrder),
            Optional.of("textSignature 1"),
            Optional.of("htmlSignature 1"));

        IdentityCreationRequest creationRequest2 = IdentityCreationRequest.fromJava(
            ALICE.asMailAddress(),
            Optional.of("Alice in borderland"),
            Optional.of(List.of(EmailAddress.from(Optional.of("reply name 2"), new MailAddress("reply2@domain.org")))),
            Optional.of(List.of(EmailAddress.from(Optional.of("bcc name 2"), new MailAddress("bcc2@domain.org")))),
            Optional.of(lowPriorityOrder),
            Optional.of("textSignature 2"),
            Optional.of("htmlSignature 2"));

        Mono.from(identityRepository.save(ALICE, creationRequest1)).block();
        Mono.from(identityRepository.save(ALICE, creationRequest2)).block();

        return identityRepository;
    }

    @Override
    public MessageManager aliceInbox() {
        return aliceInbox;
    }

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
            model,
            new JsoupHtmlTextExtractor(),
            identityRepository,
            jmapSettingsRepository,
            new RecordingMetricFactory(),
            overrideConfig);
    }

    @Override
    public void registerListenerToEventBus() {
        mailboxManager.getEventBus().register(listener);
    }

    @Override
    public JmapSettingsRepositoryJavaUtils jmapSettingsRepositoryUtils() {
        return jmapSettingsRepositoryUtils;
    }

    @Override
    public void needActionsLlmHook() {
        this.model.llOutput = "YES";
    }

    @Override
    public void noNeedActionsLlmHook() {
        this.model.llOutput = "NO";
    }

    @Override
    public Mono<Flags> readFlags(MessageId messageId, MailboxSession userSession) {
        return Mono.from(messageIdManager.getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, userSession))
            .map(MessageResult::getFlags);
    }
}
