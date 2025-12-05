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

import java.time.Duration;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.commons.configuration2.BaseHierarchicalConfiguration;
import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import reactor.core.publisher.Mono;

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
    private MessageManager aliceCustomMailbox;
    private HierarchicalConfiguration<ImmutableNode> listenerConfig;
    private StoreMailboxManager mailboxManager;
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
        MailboxPath aliceInboxPath = MailboxPath.inbox(ALICE);
        mailboxManager.createMailbox(aliceInboxPath, aliceSession).get();
        aliceInbox = mailboxManager.getMailbox(aliceInboxPath, aliceSession);
        mailboxManager.createMailbox(MailboxPath.forUser(ALICE, "customMailbox"), aliceSession).get();
        aliceCustomMailbox = mailboxManager.getMailbox(MailboxPath.forUser(ALICE, "customMailbox"), aliceSession);

        listenerConfig = new BaseHierarchicalConfiguration();

        listener = new LlmMailPrioritizationClassifierListener(
            mailboxManager,
            messageIdManager,
            new SystemMailboxesProviderImpl(mailboxManager),
            model,
            htmlTextExtractor,
            metricFactory,
            listenerConfig);
    }

    @Override
    public MessageManager aliceInbox() {
        return aliceInbox;
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
            new RecordingMetricFactory(),
            overrideConfig);
    }

    @Override
    public void registerListenerToEventBus() {
        mailboxManager.getEventBus().register(listener);
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
