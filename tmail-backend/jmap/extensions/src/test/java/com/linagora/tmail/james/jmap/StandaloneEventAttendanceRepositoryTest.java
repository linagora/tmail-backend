package com.linagora.tmail.james.jmap;

import java.util.List;
import reactor.core.publisher.Mono;

import static org.apache.james.mailbox.fixture.MailboxFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThat;

import jakarta.mail.Flags;

import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.fixture.MailboxFixture;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MessageIdManagerTestSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StandaloneEventAttendanceRepositoryTest {
    MailboxSession session;
    StandaloneEventAttendanceRepository testee;
    MessageIdManagerTestSystem messageIdManagerTestSystem;
    Mailbox mailbox;

    @BeforeEach
    void setUp() throws MailboxException {
        messageIdManagerTestSystem = createTestSystem();

        testee = new StandaloneEventAttendanceRepository(messageIdManagerTestSystem.getMessageIdManager(),
            messageIdManagerTestSystem.getMailboxManager().getSessionProvider());

        session = MailboxSessionUtil.create(ALICE);
        mailbox = messageIdManagerTestSystem.createMailbox(MailboxFixture.INBOX_ALICE, session);
    }

    @Test
    void givenAcceptedFlagIsLinkedToMailGetAttendanceStatusShouldReturnAccepted() {
        Flags flags = new Flags("$accepted");
        MessageId messageId = createMessage(flags);
        assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser(), messageId)).block())
            .isEqualTo(AttendanceStatus.Accepted);
    }

    @Test
    void givenRejectedFlagIsLinkedToMailGetAttendanceStatusShouldReturnDeclined() {
        Flags flags = new Flags("$rejected");
        MessageId messageId = createMessage(flags);
        assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser(), messageId)).block())
            .isEqualTo(AttendanceStatus.Declined);
    }

    @Test
    void givenTentativelyAcceptedFlagIsLinkedToMailGetAttendanceStatusShouldReturnTentative() {
        Flags flags = new Flags("$tentativelyaccepted");
        MessageId messageId = createMessage(flags);
        assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser(), messageId)).block())
            .isEqualTo(AttendanceStatus.Tentative);
    }

    // It should also print a warning message
    @Test
    void givenMoreThanEventAttendanceFlagIsLinkedToMailGetAttendanceStatusShouldReturnAny() {
        Flags flags = new Flags("$rejected");
        flags.add("$accepted");

        MessageId messageId = createMessage(flags);
        assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser(), messageId)).block())
            .satisfiesAnyOf(
                status -> assertThat(status).isEqualTo(AttendanceStatus.Accepted),
                status -> assertThat(status).isEqualTo(AttendanceStatus.Declined));
    }

    @Test
    void getAttendanceStatusShouldFallbackToNeedsActionWhenNoFlagIsLinkedToMail() {
        Flags flags = new Flags();
        MessageId messageId = createMessage(flags);

        assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser(), messageId)).block())
            .isEqualTo(AttendanceStatus.NeedsAction);
    }

    @Test
    void getAttendanceStatusShouldFallbackToNeedsActionWhenNoEventAttendanceFlagIsLinkedToMail() {
        Flags flags = new Flags(Flags.Flag.RECENT);
        MessageId messageId = createMessage(flags);

        assertThat(Mono.from(testee.getAttendanceStatus(mailbox.getUser(), messageId)).block())
            .isEqualTo(AttendanceStatus.NeedsAction);
    }

    @Test
    void setAttendanceStatusShouldSetAcceptedFlag() {
        Flags flags = new Flags();
        MessageId messageId = createMessage(flags);

        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.Accepted)).block();

        Flags updatedFlags = getFlags(messageId);

        assertThat(updatedFlags.contains("$accepted")).isTrue();
    }

    @Test
    void setAttendanceStatusShouldSetDeclinedFlag() {
        Flags flags = new Flags();
        MessageId messageId = createMessage(flags);

        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.Declined)).block();

        Flags updatedFlags = getFlags(messageId);
        assertThat(updatedFlags.contains("$rejected")).isTrue();
    }

    @Test
    void setAttendanceStatusShouldSetTentativeFlag() {
        Flags flags = new Flags();
        MessageId messageId = createMessage(flags);

        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.Tentative)).block();

        Flags updatedFlags = getFlags(messageId);
        assertThat(updatedFlags.contains("$tentativelyaccepted")).isTrue();
    }

    @Test
    void setAttendanceStatusShouldRemoveExistingEventAttendanceFlags() {
        Flags flags = new Flags("$accepted");
        MessageId messageId = createMessage(flags);

        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.Declined)).block();

        Flags updatedFlags = getFlags(messageId);
        assertThat(updatedFlags.contains("$accepted")).isFalse();
        assertThat(updatedFlags.contains("$rejected")).isTrue();
    }

    @Test
    void setAttendanceStatusShouldRemoveExistingEventAttendanceFlagsWhenNeedsActionSet() {
        Flags flags = new Flags("$rejected");
        MessageId messageId = createMessage(flags);

        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.NeedsAction)).block();

        Flags updatedFlags = getFlags(messageId);
        assertThat(updatedFlags.contains("$rejected")).isFalse();
        assertThat(updatedFlags.contains("$needs-action")).isTrue();
    }

    @Test
    void setAttendanceStatusShouldBeIdempotent() {
        Flags flags = new Flags();
        MessageId messageId = createMessage(flags);

        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.Accepted)).block();
        Mono.from(testee.setAttendanceStatus(mailbox.getUser(), messageId, AttendanceStatus.Accepted)).block();

        Flags updatedFlags = getFlags(messageId);

        assertThat(updatedFlags.contains("$accepted")).isTrue();
        assertThat(updatedFlags.getUserFlags().length).isEqualTo(1);
    }

    private Flags getFlags(MessageId messageId) {
        return Mono.from(messageIdManagerTestSystem.getMessageIdManager()
                .getMessagesReactive(List.of(messageId), FetchGroup.MINIMAL, session))
            .map(MessageResult::getFlags)
            .block();
    }

    private MessageId createMessage(Flags flags) {
        MessageId messageId = messageIdManagerTestSystem.persist(
            mailbox.getMailboxId(),
            MessageUid.of(111),
            flags,
            session);
        return messageId;
    }

    protected MessageIdManagerTestSystem createTestSystem() {
        InMemoryMessageId.Factory messageIdFactory = new InMemoryMessageId.Factory();

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.builder()
            .preProvisionnedFakeAuthenticator()
            .fakeAuthorizator()
            .inVmEventBus()
            .defaultAnnotationLimits()
            .defaultMessageParser()
            .scanningSearchIndex()
            .noPreDeletionHooks()
            .storeQuotaManager()
            .build();

        return new MessageIdManagerTestSystem(resources.getMessageIdManager(),
            messageIdFactory,
            resources.getMailboxManager().getMapperFactory(),
            resources.getMailboxManager());
    }
}
