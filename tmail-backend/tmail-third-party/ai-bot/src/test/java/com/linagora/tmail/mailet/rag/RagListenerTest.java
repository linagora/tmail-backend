/********************************************************************
 * As a subpart of Twake Mail, this file is edited by Linagora.    *
 * *
 * https://twake-mail.com/                                         *
 * https://linagora.com                                            *
 * *
 * This file is subject to The Affero Gnu Public License           *
 * version 3.                                                      *
 * *
 * https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 * *
 * This program is distributed in the hope that it will be         *
 * useful, but WITHOUT ANY WARRANTY; without even the implied      *
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 * PURPOSE. See the GNU Affero General Public License for          *
 * more details.                                                   *
 ********************************************************************/
package com.linagora.tmail.mailet.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;

import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;

class RagListenerTest {

    private static final Username BOB = Username.of("bob");
    private static final Username ALICE = Username.of("alice");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath ALICE_INBOX_PATH = MailboxPath.inbox(ALICE);
    private static final MailboxPath BOB_SPAM_PATH = MailboxPath.forUser(BOB, "Spam");
    private static final MailboxPath BOB_MAILBOX_PATH = MailboxPath.forUser(BOB, "mailbox");
    private static final MailboxPath BOB_TRASH_PATH = MailboxPath.forUser(BOB, "Trash");

    private static final byte[] CONTENT = "Subject: test\r\n\r\nBody of the email".getBytes(StandardCharsets.UTF_8);

    StoreMailboxManager mailboxManager;
    RagListener ragListener;
    MessageIdManager messageIdManager;
    MessageManager aliceInboxMessageManager;
    MessageManager bobMailBoxMessageManager;
    MessageManager bobInboxMessageManager;
    MessageManager spamMessageManager;
    MessageManager trashMessageManager;
    MailboxId trashMailboxId;
    MailboxId spamMailboxId;
    SystemMailboxesProviderImpl systemMailboxesProvider;
    MailboxSession bobMailboxSession;
    MailboxSession aliceMailboxSession;
    UpdatableTickingClock clock;
    MailboxSessionMapperFactory mapperFactory;
    MailboxId bobInboxId;
    MailboxId bobMailBoxId;
    MailboxId aliceInboxId;
    MemoryEventDeadLetters eventDeadLetters;
    ListAppender<ILoggingEvent> listAppender;
    Logger logger;
    RagListenerConfiguration ragListenerConfiguration;

    @BeforeEach
    void setup() throws Exception {
        logger = (Logger) LoggerFactory.getLogger(RagListener.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        clock = new UpdatableTickingClock(Instant.now());
        mapperFactory = new InMemoryMailboxSessionMapperFactory(clock);
        RetryBackoffConfiguration backoffConfiguration = RetryBackoffConfiguration.builder()
            .maxRetries(2)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0.5)
            .build();
        eventDeadLetters = new MemoryEventDeadLetters();

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

        mailboxManager = resources.getMailboxManager();;
        messageIdManager = spy(resources.getMessageIdManager());
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");
        authenticator.addUser(ALICE, "12345");

        bobMailboxSession = MailboxSessionUtil.create(BOB);
        aliceMailboxSession = MailboxSessionUtil.create(ALICE);
        bobInboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, bobMailboxSession).get();
        bobMailBoxId = mailboxManager.createMailbox(BOB_MAILBOX_PATH, bobMailboxSession).get();
        aliceInboxId = mailboxManager.createMailbox(ALICE_INBOX_PATH, aliceMailboxSession).get();
        spamMailboxId = mailboxManager.createMailbox(BOB_SPAM_PATH, bobMailboxSession).get();
        trashMailboxId = mailboxManager.createMailbox(BOB_TRASH_PATH, bobMailboxSession).get();
        spamMessageManager = mailboxManager.getMailbox(spamMailboxId, bobMailboxSession);
        trashMessageManager = mailboxManager.getMailbox(trashMailboxId, bobMailboxSession);
        bobInboxMessageManager = mailboxManager.getMailbox(bobInboxId, bobMailboxSession);
        bobMailBoxMessageManager = mailboxManager.getMailbox(bobMailBoxId, bobMailboxSession);
        aliceInboxMessageManager = mailboxManager.getMailbox(aliceInboxId, aliceMailboxSession);
        systemMailboxesProvider = new SystemMailboxesProviderImpl(mailboxManager);

        }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContent() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.of(List.of(BOB)));
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains(
                "RAG Listener triggered for mailbox: " + bobInboxId,
                "RAG Listener successfully processed mailContent ***** Body of the email *****");
    }

    @Test
    void reactiveEventShouldRetuenNullWhenEvendIsNotAppended() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.of(List.of(BOB)));
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult messageResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        mailboxManager.moveMessagesReactive(
            MessageRange.from(messageResult.getId().getUid()),
            bobInboxId,
            bobMailBoxId,
            bobMailboxSession
        ).collectList().block();

        List<MessageRange> mess = mailboxManager.copyMessages(MessageRange.from(messageResult.getId().getUid()), bobInboxId,bobMailBoxId,bobMailboxSession);

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains(
                "RAG Listener triggered for mailbox: " + bobInboxId,
                "RAG Listener successfully processed mailContent ***** Body of the email *****",
                "RAG Listener triggered for mailbox deletion: " + bobInboxId);
    }

    @Test
    void reactiveEventShouldRetuenNullWhenEvendIsSpam() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.of(List.of(BOB)));
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult messageResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void reactiveEventShouldRetuenNullWhenEvendIsTrush() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.of(List.of(BOB)));
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult messageResult = trashMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void reactiveEventShouldReturnDeletedWhenDletedEvent() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.of(List.of(BOB)));
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult messageResult = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        bobInboxMessageManager.delete(List.of(messageResult.getId().getUid()), bobMailboxSession);

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains("RAG Listener triggered for mailbox deletion: " + bobInboxId );
    }

    @Test
    void reactiveEventShouldSkipListenerWhenUserNotInWhiteList() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.of(List.of(BOB)));
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult messageResult = aliceInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            aliceMailboxSession);

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains("RAG Listener skipped for user: alice");
    }

    @Test
    void reactiveEventShouldAllowAllUsersWhenWhiteListIsEmpty() throws Exception {
        ragListenerConfiguration = new RagListenerConfiguration(Optional.empty());
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, ragListenerConfiguration);
        mailboxManager.getEventBus().register(ragListener);

        MessageManager.AppendResult messageResult = aliceInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            aliceMailboxSession);
        MessageManager.AppendResult messageResult2 = bobInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(CONTENT)),
            bobMailboxSession);

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains("RAG Listener triggered for mailbox: " + aliceInboxId,
                "RAG Listener successfully processed mailContent ***** Body of the email *****",
                "RAG Listener triggered for mailbox: " + bobInboxId,
                "RAG Listener successfully processed mailContent ***** Body of the email *****");
    }

}