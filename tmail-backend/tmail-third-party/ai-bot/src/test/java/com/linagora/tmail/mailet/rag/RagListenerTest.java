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
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.core.read.ListAppender;

import com.google.common.collect.ImmutableSortedMap;

import jakarta.mail.Flags;
import jakarta.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

class RagListenerTest {

    private static final Username BOB = Username.of("bob");
    private static final Username ALICE = Username.of("alice");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath ALICE_INBOX_PATH = MailboxPath.inbox(ALICE);
    private static final MailboxPath BOB_SPAM_PATH = MailboxPath.forUser(BOB, "Spam");
    private static final MailboxPath BOB_TRASH_PATH = MailboxPath.forUser(BOB, "Trash");
    private static final TestMessageId MESSAGE_ID = TestMessageId.of(18);
    private static final MessageUid MESSAGE_UID_1 = MessageUid.of(25);
    private static final byte[] CONTENT = "Subject: test\r\n\r\nBody of the email".getBytes(StandardCharsets.UTF_8);

    RagListener ragListener;
    MailboxManager mailboxManager;
    MessageIdManager messageIdManager;
    MessageManager aliceInboxMessageManager;
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
    MailboxId aliceInboxId;
    MemoryEventDeadLetters eventDeadLetters;
    ListAppender<ILoggingEvent> listAppender;
    Logger logger;

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

        mailboxManager = resources.getMailboxManager();
        messageIdManager = spy(resources.getMessageIdManager());
        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");
        authenticator.addUser(ALICE, "12345");

        bobMailboxSession = MailboxSessionUtil.create(BOB);
        aliceMailboxSession = MailboxSessionUtil.create(ALICE);
        bobInboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, bobMailboxSession).get();
        aliceInboxId = mailboxManager.createMailbox(ALICE_INBOX_PATH, aliceMailboxSession).get();
        spamMailboxId = mailboxManager.createMailbox(BOB_SPAM_PATH, bobMailboxSession).get();
        trashMailboxId = mailboxManager.createMailbox(BOB_TRASH_PATH, bobMailboxSession).get();
        spamMessageManager = mailboxManager.getMailbox(spamMailboxId, bobMailboxSession);
        trashMessageManager = mailboxManager.getMailbox(trashMailboxId, bobMailboxSession);
        bobInboxMessageManager = mailboxManager.getMailbox(bobInboxId, bobMailboxSession);
        aliceInboxMessageManager = mailboxManager.getMailbox(aliceInboxId, aliceMailboxSession);
        systemMailboxesProvider = new SystemMailboxesProviderImpl(mailboxManager);
        Set<Username> whitelist = Set.of(BOB);
        ragListener = new RagListener(mailboxManager, messageIdManager, systemMailboxesProvider, whitelist);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    @Test
    void reactiveEventShouldProcessAddedEventAndExtractContent() throws Exception {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .mailboxId(bobInboxId)
            .flags(new Flags())
            .bodyStartOctet(100)
            .internalDate(new Date(1433628000000L))
            .size(25)
            .content(new ByteContent(CONTENT))
            .properties(new PropertyBuilder())
            .modseq( ModSeq.of(42L))
            .messageId(MESSAGE_ID)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID))
            .uid(MESSAGE_UID_1)
            .build();

        MessageManager.AppendResult messageResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(message.getFullContent().readAllBytes())),
            bobMailboxSession);

        MessageMetaData messageMetaData = new MessageMetaData(messageResult.getId().getUid(),
            message.getModSeq(),
            message.metaData().getFlags(),
            25,
            message.getInternalDate(),
            message.getSaveDate(),
            messageResult.getId().getMessageId(),
            message.getThreadId());

        MailboxEvents.Added added= new MailboxEvents.Added(bobMailboxSession.getSessionId(),
            BOB,
            BOB_INBOX_PATH,
            bobInboxId,
            ImmutableSortedMap.of(messageResult.getId().getUid(), messageMetaData),
            Event.EventId.random(),
            true,
            true, Optional.empty());

        Mono.from(ragListener.reactiveEvent(added)).block();

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains(
                "RAG Listener triggered for mailbox: 1",
                "RAG Listener successfully processed mailContent ***** Body of the email *****");
    }

    @Test
    void reactiveEventShouldRetuenNullWhenEvendIsNotAppended() throws Exception {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .mailboxId(bobInboxId)
            .flags(new Flags())
            .bodyStartOctet(100)
            .internalDate(new Date(1433628000000L))
            .size(25)
            .content(new ByteContent(CONTENT))
            .properties(new PropertyBuilder())
            .modseq( ModSeq.of(42L))
            .messageId(MESSAGE_ID)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID))
            .uid(MESSAGE_UID_1)
            .build();

        MessageManager.AppendResult messageResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(message.getFullContent().readAllBytes())),
            bobMailboxSession);

        MessageMetaData messageMetaData = new MessageMetaData(messageResult.getId().getUid(),
            message.getModSeq(),
            message.metaData().getFlags(),
            25,
            message.getInternalDate(),
            message.getSaveDate(),
            messageResult.getId().getMessageId(),
            message.getThreadId());

        MailboxEvents.Added added= new MailboxEvents.Added(bobMailboxSession.getSessionId(),
            BOB, BOB_INBOX_PATH, bobInboxId, ImmutableSortedMap.of(messageResult.getId().getUid(), messageMetaData),
            Event.EventId.random(), true, false, Optional.empty());

        Mono.from(ragListener.reactiveEvent(added)).block();

        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void reactiveEventShouldRetuenNullWhenEvendIsSpam() throws Exception {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .mailboxId(spamMailboxId)
            .flags(new Flags())
            .bodyStartOctet(100)
            .internalDate(new Date(1433628000000L))
            .size(25)
            .content(new ByteContent(CONTENT))
            .properties(new PropertyBuilder())
            .modseq(ModSeq.of(42L))
            .messageId(MESSAGE_ID)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID))
            .uid(MESSAGE_UID_1)
            .build();

        MessageManager.AppendResult messageResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(message.getFullContent().readAllBytes())),
            bobMailboxSession);

        MessageMetaData messageMetaData = new MessageMetaData(messageResult.getId().getUid(),
            message.getModSeq(),
            message.metaData().getFlags(),
            25,
            message.getInternalDate(),
            message.getSaveDate(),
            messageResult.getId().getMessageId(),
            message.getThreadId());

        MailboxEvents.Added added = new MailboxEvents.Added(bobMailboxSession.getSessionId(),
            BOB, BOB_SPAM_PATH, spamMailboxId, ImmutableSortedMap.of(messageResult.getId().getUid(), messageMetaData),
            Event.EventId.random(), true, true, Optional.empty());

        Mono.from(ragListener.reactiveEvent(added)).block();
        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void reactiveEventShouldRetuenNullWhenEvendIsTrush() throws Exception {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .mailboxId(trashMailboxId)
            .flags(new Flags())
            .bodyStartOctet(100)
            .internalDate(new Date(1433628000000L))
            .size(25)
            .content(new ByteContent(CONTENT))
            .properties(new PropertyBuilder())
            .modseq(ModSeq.of(42L))
            .messageId(MESSAGE_ID)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID))
            .uid(MESSAGE_UID_1)
            .build();

        MessageManager.AppendResult messageResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(message.getFullContent().readAllBytes())),
            bobMailboxSession);

        MessageMetaData messageMetaData = new MessageMetaData(messageResult.getId().getUid(),
            message.getModSeq(),
            message.metaData().getFlags(),
            25,
            message.getInternalDate(),
            message.getSaveDate(),
            messageResult.getId().getMessageId(),
            message.getThreadId());

        MailboxEvents.Added added = new MailboxEvents.Added(bobMailboxSession.getSessionId(),
            BOB, BOB_TRASH_PATH, spamMailboxId, ImmutableSortedMap.of(messageResult.getId().getUid(), messageMetaData),
            Event.EventId.random(), true, true, Optional.empty());

        Mono.from(ragListener.reactiveEvent(added)).block();
        assertThat(listAppender.list).isEmpty();
    }

    @Test
    void reactiveEventShouldReturnDeletedWhenDletedEvent() throws Exception {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .mailboxId(trashMailboxId)
            .flags(new Flags())
            .bodyStartOctet(100)
            .internalDate(new Date(1433628000000L))
            .size(25)
            .content(new ByteContent(CONTENT))
            .properties(new PropertyBuilder())
            .modseq(ModSeq.of(42L))
            .messageId(MESSAGE_ID)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID))
            .uid(MESSAGE_UID_1)
            .build();

        MessageManager.AppendResult messageResult = spamMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(message.getFullContent().readAllBytes())),
            bobMailboxSession);

        MessageMetaData messageMetaData = new MessageMetaData(messageResult.getId().getUid(),
            message.getModSeq(),
            message.metaData().getFlags(),
            25,
            message.getInternalDate(),
            message.getSaveDate(),
            messageResult.getId().getMessageId(),
            message.getThreadId());

        MailboxEvents.Expunged expunged = new MailboxEvents.Expunged(bobMailboxSession.getSessionId(),
            BOB, BOB_TRASH_PATH, spamMailboxId, ImmutableSortedMap.of(messageResult.getId().getUid(), messageMetaData),
            Event.EventId.random(),  Optional.empty());

        Mono.from(ragListener.reactiveEvent(expunged)).block();
        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains("RAG Listener triggered for mailbox deletion: " + expunged.getMailboxId().toString() );
    }
    @Test
    void reactiveEventShouldSkipListenerWhenUserNotInWhiteList() throws Exception {
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .mailboxId(aliceInboxId)
            .flags(new Flags())
            .bodyStartOctet(100)
            .internalDate(new Date(1433628000000L))
            .size(25)
            .content(new ByteContent(CONTENT))
            .properties(new PropertyBuilder())
            .modseq( ModSeq.of(42L))
            .messageId(MESSAGE_ID)
            .threadId(ThreadId.fromBaseMessageId(MESSAGE_ID))
            .uid(MESSAGE_UID_1)
            .build();

        MessageManager.AppendResult messageResult = aliceInboxMessageManager.appendMessage(
            MessageManager.AppendCommand.from(new SharedByteArrayInputStream(message.getFullContent().readAllBytes())),
            aliceMailboxSession);

        MessageMetaData messageMetaData = new MessageMetaData(messageResult.getId().getUid(),
            message.getModSeq(),
            message.metaData().getFlags(),
            25,
            message.getInternalDate(),
            message.getSaveDate(),
            messageResult.getId().getMessageId(),
            message.getThreadId());

        MailboxEvents.Added added= new MailboxEvents.Added(aliceMailboxSession.getSessionId(),
            ALICE,
            ALICE_INBOX_PATH,
            aliceInboxId,
            ImmutableSortedMap.of(messageResult.getId().getUid(), messageMetaData),
            Event.EventId.random(),
            true,
            true, Optional.empty());

        Mono.from(ragListener.reactiveEvent(added)).block();

        assertThat(listAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .contains("RAG Listener skipped for user: alice");
    }
}