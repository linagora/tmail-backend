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

package com.linagora.tmail.webadmin.contact.autocomplete;

import static com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingTask.RunningOptions.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.field.address.DefaultAddressParser;
import org.apache.james.task.Task;
import org.apache.james.user.api.UsersRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.james.jmap.contact.ContactAddIndexingProcessor;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingContext;
import com.linagora.tmail.webadmin.contact.aucomplete.ContactIndexingService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ContactIndexingServiceTest {
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final MailboxPath BOB_SENT_MAILBOX = MailboxPath.forUser(BOB, "Sent");
    private static final AccountId BOB_ACCOUNT_ID = AccountId.fromUsername(BOB);
    private static final Username ANDRE = Username.of("andre@doamin.tld");
    private static final AccountId ANDRE_ACCOUNT_ID = AccountId.fromUsername(ANDRE);
    private static final ContactFields DAVID_CONTACT_FIELDS = Throwing.supplier(() -> ContactFields.of(new MailAddress("dvid@domain.tld"), "DAVID")).get();
    private static final ContactFields NARUTO_CONTACT_FIELDS = Throwing.supplier(() -> ContactFields.of(new MailAddress("naruto@domain.tld"), "Naruto")).get();
    private static final EmailAddressContact DAVID_EMAIL_ADDRESS_CONTACT = EmailAddressContact.of(DAVID_CONTACT_FIELDS);

    private ContactIndexingService testee;
    private UsersRepository usersRepository;
    private EmailAddressContactSearchEngine contactListPublisher;
    private ContactAddIndexingProcessor contactAddIndexingProcessor;
    private InMemoryMailboxManager mailboxManager;
    private MailboxSession bobSession;

    @BeforeEach
    void setup() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        usersRepository = mock(UsersRepository.class);

        contactListPublisher = mock(EmailAddressContactSearchEngine.class);
        contactAddIndexingProcessor = mock(ContactAddIndexingProcessor.class);
        when(contactAddIndexingProcessor.process(any(), any())).thenReturn(Flux.empty());
        testee = new ContactIndexingService(usersRepository,
            mailboxManager,
            resources.getMailboxManager().getMapperFactory(),
            contactListPublisher, contactAddIndexingProcessor);

        bobSession = mailboxManager.createSystemSession(BOB);
        mailboxManager.createMailbox(BOB_SENT_MAILBOX, bobSession);
    }

    @Test
    void shouldNotCallIndexingProcessorWhenSentMailboxIsEmpty() {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);

        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 0, 0, ImmutableList.of()));
        verify(contactAddIndexingProcessor, times(0)).process(eq(BOB),
            any(ContactFields.class));
    }


    @Test
    void shouldCallIndexingProcessorWhenContactIsNotIndexed() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        // Add a message to the Sent mailbox
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("DAVID <dvid@domain.tld>"), bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 1, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(DAVID_CONTACT_FIELDS));
    }

    @Test
    void shouldNotCallIndexingProcessorWhenContactIsAlreadyIndexed() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of(DAVID_EMAIL_ADDRESS_CONTACT)));

        // Add a message to the Sent mailbox
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("DAVID <dvid@domain.tld>"), bobSession);

        ContactIndexingContext context = new ContactIndexingContext();
        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 0, 0, ImmutableList.of()));
        verify(contactAddIndexingProcessor, times(0)).process(eq(BOB),
            eq(DAVID_CONTACT_FIELDS));
    }

    @Test
    void shouldCallIndexingForCCAddress() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        // Add a message to the Sent mailbox with a CC address
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                    .setSubject("Should index TO address")
                    .setTo("DAVID <dvid@domain.tld>")
                    .setCc(DefaultAddressParser.DEFAULT.parseMailbox("Naruto <naruto@domain.tld>"))
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .build()),
                bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 2, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(DAVID_CONTACT_FIELDS));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(NARUTO_CONTACT_FIELDS));
    }

    @Test
    void shouldCallIndexingForBCCAddress() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        // Add a message to the Sent mailbox with a BCC address
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                    .setSubject("Should index TO address")
                    .setTo("DAVID <dvid@domain.tld>")
                    .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("Naruto <naruto@domain.tld>"))
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .build()),
                bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 2, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(DAVID_CONTACT_FIELDS));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(NARUTO_CONTACT_FIELDS));
    }

    @Test
    void shouldCallIndexingProcessorWhenRecipientAddressIsCollectionAndIsNotIndexed() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                    .setSubject("Should index TO address")
                    .setTo("TO1 <to1@domain.tld>", "TO2 <to2@domain.tld>")
                    .setCc(DefaultAddressParser.DEFAULT.parseMailbox("CC1 <cc1@domain.tld>"), DefaultAddressParser.DEFAULT.parseMailbox("CC2 <cc2@domain.tld>"))
                    .setBcc(DefaultAddressParser.DEFAULT.parseMailbox("BCC1 <bcc1@domain.tld>"), DefaultAddressParser.DEFAULT.parseMailbox("BCC2 <bcc2@domain.tld>"))
                    .setBody("testmail", StandardCharsets.UTF_8)
                    .build()),
                bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 6, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("to1@domain.tld", "TO1")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("to2@domain.tld", "TO2")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("cc1@domain.tld", "CC1")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("cc1@domain.tld", "CC1")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("bcc1@domain.tld", "BCC1")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("bcc2@domain.tld", "BCC2")));
    }

    @Test
    void shouldCallIndexingProcessorWhenHeaderAddressDoesNotContainDisplayName() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("dvid@domain.tld"), bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 1, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(contactFields("dvid@domain.tld")));
    }

    @Test
    void shouldCallIndexingProcessorForAllUsers() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB, ANDRE));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));
        when(contactListPublisher.list(ANDRE_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        // Add a message to the BOB Sent mailbox
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("dvid@domain.tld"), bobSession);

        // Add a message to the ANDRE Sent mailbox
        MailboxSession andreSession = mailboxManager.createSystemSession(ANDRE);
        MailboxPath andreSentMailbox = MailboxPath.forUser(ANDRE, "Sent");
        mailboxManager.createMailbox(andreSentMailbox, andreSession);
        mailboxManager.getMailbox(andreSentMailbox, andreSession)
            .appendMessage(appendCommandTO(("Naruto <naruto@domain.tld>")), andreSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(2, 2, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB),
            eq(contactFields("dvid@domain.tld")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(ANDRE),
            eq(contactFields("naruto@domain.tld", "Naruto")));
    }

    @Test
    void shouldCallIndexingProcessorWhenMixCases() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB, ANDRE));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of(DAVID_EMAIL_ADDRESS_CONTACT)));
        when(contactListPublisher.list(ANDRE_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of(
            EmailAddressContact.of(contactFields("sakura@domain.tld")),
            EmailAddressContact.of(contactFields("madara@domain.tld")))));

        // Given BOB contact: indexed: david , not indexed: naruto
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("dvid@domain.tld"), bobSession);

        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("sasuke@domain.tld"), bobSession);

        // Given ANDRE contact: indexed: sakura, madara , not indexed: david, sasuke
        MailboxSession andreSession = mailboxManager.createSystemSession(ANDRE);
        MailboxPath andreSentMailbox = MailboxPath.forUser(ANDRE, "Sent");
        mailboxManager.createMailbox(andreSentMailbox, andreSession);
        mailboxManager.getMailbox(andreSentMailbox, andreSession)
            .appendMessage(appendCommandTO("dvid@domain.tld", "sasuke@domain.tld", "sakura@domain.tld", "madara@domain.tld"), andreSession);

        ContactIndexingContext context = new ContactIndexingContext();
        // When indexing all contacts
        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();
        assertThat(result).isEqualTo(Task.Result.COMPLETED);

        // Then 
        assertThat(context.snapshot()).isEqualTo(new ContactIndexingContext.Snapshot(2, 3, 0, ImmutableList.of()));

        verify(contactAddIndexingProcessor, times(0)).process(eq(BOB), eq(contactFields("dvid@domain.tld")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(contactFields("sasuke@domain.tld")));
        verify(contactAddIndexingProcessor, times(0)).process(eq(ANDRE), eq(contactFields("sakura@domain.tld")));
        verify(contactAddIndexingProcessor, times(0)).process(eq(ANDRE), eq(contactFields("madara@domain.tld")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(ANDRE), eq(contactFields("dvid@domain.tld")));
        verify(contactAddIndexingProcessor, times(1)).process(eq(ANDRE), eq(contactFields("sasuke@domain.tld")));
    }

    @Test
    void shouldNotThrowWhenAbsentSentMailbox() throws MailboxException {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));

        mailboxManager.deleteMailbox(BOB_SENT_MAILBOX, bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();

        assertThat(result).isEqualTo(Task.Result.COMPLETED);

        assertThat(context.snapshot()).isEqualTo(new ContactIndexingContext.Snapshot(0, 0, 0, ImmutableList.of()));
        verify(contactAddIndexingProcessor, times(0)).process(eq(BOB), any(ContactFields.class));
    }

    @Test
    void shouldReturnPARTIALWhenProcessorFails() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.fromIterable(List.of()));
        when(contactAddIndexingProcessor.process(eq(BOB), any())).thenReturn(Mono.error(() -> new RuntimeException("mocked exception")));

        // Add a message to the Sent mailbox
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("DAVID <dvid@domain.tld>"), bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();

        assertThat(result).isEqualTo(Task.Result.PARTIAL);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 0, 1, ImmutableList.of(BOB.asString())));

        verify(contactAddIndexingProcessor, times(1)).process(eq(BOB), eq(DAVID_CONTACT_FIELDS));
    }

    @Test
    void shouldReturnPARTIALWhenContactListPublisherFails() throws Exception {
        when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
        when(contactListPublisher.list(BOB_ACCOUNT_ID)).thenReturn(Flux.error(() -> new RuntimeException("mocked exception")));

        // Add a message to the Sent mailbox
        mailboxManager.getMailbox(BOB_SENT_MAILBOX, bobSession)
            .appendMessage(appendCommandTO("DAVID <dvid@domain.tld>"), bobSession);

        ContactIndexingContext context = new ContactIndexingContext();

        Task.Result result = testee.indexAllContacts(DEFAULT, context).block();

        assertThat(result).isEqualTo(Task.Result.PARTIAL);
        assertThat(context.snapshot())
            .isEqualTo(new ContactIndexingContext.Snapshot(1, 0, 0, ImmutableList.of(BOB.asString())));

        verify(contactAddIndexingProcessor, times(0)).process(eq(BOB), eq(DAVID_CONTACT_FIELDS));
    }

    private MessageManager.AppendCommand appendCommandTO(String... to) {
        return Throwing.supplier(() -> MessageManager.AppendCommand.from(Message.Builder.of()
            .setSubject(UUID.randomUUID().toString())
            .setTo(to)
            .setBody("testmail", StandardCharsets.UTF_8)
            .build())).get();
    }

    private ContactFields contactFields(String email) {
        return Throwing.supplier(() -> ContactFields.of(new MailAddress(email), null)).get();
    }

    private ContactFields contactFields(String email, String name) {
        return Throwing.supplier(() -> ContactFields.of(new MailAddress(email), name)).get();
    }
}
