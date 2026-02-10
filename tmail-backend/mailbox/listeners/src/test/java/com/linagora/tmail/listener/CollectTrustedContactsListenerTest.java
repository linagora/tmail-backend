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

package com.linagora.tmail.listener;

import static com.linagora.tmail.listener.CollectTrustedContactsListener.TO_BE_COLLECTED_FLAG;
import static jakarta.mail.Flags.Flag.FLAGGED;
import static jakarta.mail.Flags.Flag.SEEN;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.field.address.DefaultAddressParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;
import com.linagora.tmail.james.jmap.contact.ContactFields;

import reactor.core.publisher.Mono;

class CollectTrustedContactsListenerTest {
    private static class CapturingContactAddIndexingProcessor implements ContactAddIndexingProcessor {
        private final List<Invocation> invocations = new ArrayList<>();

        @Override
        public Publisher<Void> process(Username username, ContactFields contactFields) {
            return Mono.fromRunnable(() -> invocations.add(new Invocation(username, contactFields)));
        }

        List<Username> indexedUsernames() {
            return invocations.stream()
                .map(Invocation::username)
                .toList();
        }

        List<String> indexedAddresses() {
            return invocations.stream()
                .map(Invocation::contactFields)
                .map(contact -> contact.address().asString())
                .toList();
        }

        List<String> indexedFirstnames() {
            return invocations.stream()
                .map(Invocation::contactFields)
                .map(ContactFields::firstname)
                .toList();
        }

        int invocationCount() {
            return invocations.size();
        }
    }

    private record Invocation(Username username, ContactFields contactFields) {
    }

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_SENT_PATH = MailboxPath.forUser(BOB, "Sent");
    private static final String CAROL_ADDRESS = "carol@domain.tld";
    private static final String DAVID_ADDRESS = "david@domain.tld";
    private static final String ALICE_ADDRESS = "alice@domain.tld";

    private MailboxManager mailboxManager;
    private MailboxSession bobMailboxSession;
    private MailboxId bobInboxId;
    private CapturingContactAddIndexingProcessor capturingContactAddIndexingProcessor;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        bobMailboxSession = mailboxManager.createSystemSession(BOB);
        bobInboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, bobMailboxSession).orElseThrow();
        capturingContactAddIndexingProcessor = new CapturingContactAddIndexingProcessor();
        CollectTrustedContactsListener testee = new CollectTrustedContactsListener(mailboxManager, resources.getMessageIdManager(), capturingContactAddIndexingProcessor);
        resources.getEventBus().register(testee);
    }

    @Test
    void shouldIndexContactsWhenMessageAddedWithToBeCollectedFlag() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        Flags flags = new Flags(TO_BE_COLLECTED_FLAG);
        appendMessage(bobInboxId, message, flags);

        assertThat(capturingContactAddIndexingProcessor.indexedUsernames()).containsOnly(BOB);
        assertThat(capturingContactAddIndexingProcessor.indexedAddresses())
            .containsExactlyInAnyOrder(ALICE_ADDRESS, CAROL_ADDRESS, DAVID_ADDRESS);
        assertThat(capturingContactAddIndexingProcessor.indexedFirstnames())
            .containsExactlyInAnyOrder("Alice", "Carol", "David");
    }

    @Test
    void shouldIndexContactsWhenMessageAddedWithImportantFlag() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        Flags flags = new Flags(FLAGGED);
        appendMessage(bobInboxId, message, flags);

        assertThat(capturingContactAddIndexingProcessor.indexedUsernames()).containsOnly(BOB);
        assertThat(capturingContactAddIndexingProcessor.indexedAddresses())
            .containsExactlyInAnyOrder(ALICE_ADDRESS, CAROL_ADDRESS, DAVID_ADDRESS);
    }

    @Test
    void shouldIndexContactsWhenFlagsUpdatedWithToBeCollectedFlagAdded() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        ComposedMessageId messageId = appendMessage(bobInboxId, message, new Flags());

        addFlags(messageId, new Flags(TO_BE_COLLECTED_FLAG));

        assertThat(capturingContactAddIndexingProcessor.indexedUsernames()).containsOnly(BOB);
        assertThat(capturingContactAddIndexingProcessor.indexedAddresses())
            .containsExactlyInAnyOrder(ALICE_ADDRESS, CAROL_ADDRESS, DAVID_ADDRESS);
    }

    @Test
    void shouldIndexContactsWhenFlagsUpdatedWithImportantFlagAdded() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        ComposedMessageId messageId = appendMessage(bobInboxId, message, new Flags());

        addFlags(messageId, new Flags(FLAGGED));

        assertThat(capturingContactAddIndexingProcessor.indexedUsernames()).containsOnly(BOB);
        assertThat(capturingContactAddIndexingProcessor.indexedAddresses())
            .containsExactlyInAnyOrder(ALICE_ADDRESS, CAROL_ADDRESS, DAVID_ADDRESS);
    }

    @Test
    void shouldNotIndexContactsWhenMessageAddedWithoutTrustFlags() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        Flags flags = new Flags(SEEN);
        appendMessage(bobInboxId, message, flags);

        assertThat(capturingContactAddIndexingProcessor.indexedAddresses()).isEmpty();
    }

    @Test
    void shouldNotIndexContactsWhenFlagsUpdatedWithoutTrustFlags() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        ComposedMessageId messageId = appendMessage(bobInboxId, message, new Flags());

        // add the SEEN flag, not trust flags
        addFlags(messageId, new Flags(SEEN));

        assertThat(capturingContactAddIndexingProcessor.indexedAddresses()).isEmpty();
    }

    @Test
    void shouldNotReIndexContactsWhenFlagUpdatedWithExistingTrustFlags() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();

        // Given the message is added with trust flags
        ComposedMessageId messageId = appendMessage(bobInboxId, message, new Flags(TO_BE_COLLECTED_FLAG));
        int indexContactsInvocationBeforeFlagUpdate = capturingContactAddIndexingProcessor.invocationCount();

        // add the SEEN flag
        addFlags(messageId, new Flags(SEEN));
        int indexContactsInvocationAfterFlagUpdate = capturingContactAddIndexingProcessor.invocationCount();

        assertThat(indexContactsInvocationAfterFlagUpdate).isEqualTo(indexContactsInvocationBeforeFlagUpdate);
    }

    @Test
    void shouldNotReIndexContactsWhenFlagsUpdatedWithAnotherTrustFlagAdded() throws Exception {
        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();

        // Given the message is added with the $to_be_collected flag
        ComposedMessageId messageId = appendMessage(bobInboxId, message, new Flags(TO_BE_COLLECTED_FLAG));
        int indexContactsInvocationBeforeFlagUpdate = capturingContactAddIndexingProcessor.invocationCount();

        // add the important flag
        addFlags(messageId, new Flags(FLAGGED));
        int indexContactsInvocationAfterFlagUpdate = capturingContactAddIndexingProcessor.invocationCount();

        assertThat(indexContactsInvocationAfterFlagUpdate).isEqualTo(indexContactsInvocationBeforeFlagUpdate);
    }

    @Test
    void shouldNotReIndexContactsWhenTrustedMessageIsMoved() throws Exception {
        mailboxManager.createMailbox(BOB_SENT_PATH, bobMailboxSession);
        MailboxId bobSentId = mailboxManager.getMailbox(BOB_SENT_PATH, bobMailboxSession).getId();

        Message message = Message.Builder.of()
            .setFrom("Alice <alice@domain.tld>")
            .setTo(BOB.asString(), "Carol <carol@domain.tld>")
            .setCc(DefaultAddressParser.DEFAULT.parseMailbox("David <david@domain.tld>"))
            .setBody("Body", StandardCharsets.UTF_8)
            .build();
        ComposedMessageId messageId = appendMessage(bobInboxId, message, new Flags(TO_BE_COLLECTED_FLAG));
        int indexContactsInvocationBeforeMove = capturingContactAddIndexingProcessor.invocationCount();

        mailboxManager.moveMessages(MessageRange.one(messageId.getUid()), bobInboxId, bobSentId, bobMailboxSession);
        int indexContactsInvocationAfterMove = capturingContactAddIndexingProcessor.invocationCount();

        assertThat(indexContactsInvocationAfterMove).isEqualTo(indexContactsInvocationBeforeMove);
    }

    @Test
    void reactiveEventShouldSkipMalformedAddressesAndContinue() throws Exception {
        appendMessage(bobInboxId, new Flags(TO_BE_COLLECTED_FLAG),
            "From: Alice <alice@domain.tld>\r\n"
                + "To: " + BOB.asString() + "\r\n"
                + "Cc: malformed-address, Carol <carol@domain.tld>\r\n"
                + "Subject: trusted contacts\r\n"
                + "\r\n"
                + "Body");

        assertThat(capturingContactAddIndexingProcessor.indexedAddresses())
            .containsExactlyInAnyOrder(ALICE_ADDRESS, CAROL_ADDRESS);
    }

    private ComposedMessageId appendMessage(MailboxId mailboxId, Message message, Flags flags) throws Exception {
        return mailboxManager.getMailbox(mailboxId, bobMailboxSession)
            .appendMessage(MessageManager.AppendCommand.builder()
                .withFlags(flags)
                .build(message), bobMailboxSession)
            .getId();
    }

    private void appendMessage(MailboxId mailboxId, Flags flags, String rawMessage) throws Exception {
        mailboxManager.getMailbox(mailboxId, bobMailboxSession)
            .appendMessage(new ByteArrayInputStream(rawMessage.getBytes(StandardCharsets.UTF_8)),
                new Date(),
                bobMailboxSession,
                true,
                flags);
    }

    private void addFlags(ComposedMessageId composedMessageId, Flags flags) throws Exception {
        mailboxManager.getMailbox(bobInboxId, bobMailboxSession)
            .setFlags(flags, MessageManager.FlagsUpdateMode.ADD, MessageRange.one(composedMessageId.getUid()), bobMailboxSession);
    }

}
