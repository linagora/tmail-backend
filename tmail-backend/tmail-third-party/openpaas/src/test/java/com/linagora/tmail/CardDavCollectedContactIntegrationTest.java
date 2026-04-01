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

package com.linagora.tmail;

import static com.linagora.tmail.listener.CollectTrustedContactsListener.TO_BE_COLLECTED_FLAG;
import static org.apache.james.mailets.configuration.Constants.DEFAULT_DOMAIN;
import static org.apache.james.mailets.configuration.Constants.LOCALHOST_IP;
import static org.apache.james.mailets.configuration.Constants.PASSWORD;
import static org.apache.james.mailets.configuration.Constants.awaitAtMostOneMinute;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.UUID;

import jakarta.inject.Singleton;
import jakarta.mail.Flags;
import jakarta.mail.MessagingException;

import org.apache.james.MemoryJamesServerMain;
import org.apache.james.core.Username;
import org.apache.james.core.builder.MimeMessageBuilder;
import org.apache.james.jmap.routes.BlobResolver;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailets.TemporaryJamesServer;
import org.apache.james.mailets.configuration.CommonProcessors;
import org.apache.james.mailets.configuration.MailetConfiguration;
import org.apache.james.mailets.configuration.ProcessorConfiguration;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.transport.matchers.All;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.util.Modules;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.dav.CardDavUtils;
import com.linagora.tmail.dav.DavServerExtension;
import com.linagora.tmail.dav.DavUid;
import com.linagora.tmail.dav.OpenPaaSUserId;
import com.linagora.tmail.dav.WireMockOpenPaaSServerExtension;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngineModule;
import com.linagora.tmail.listener.CollectTrustedContactsListenerModule;
import com.linagora.tmail.mailet.CardDavCollectedContact;

import reactor.core.publisher.Mono;

public class CardDavCollectedContactIntegrationTest {
    private static final Username ALICE = Username.of("alice@" + DEFAULT_DOMAIN);
    private static final Username BOB = Username.of("bob@" + DEFAULT_DOMAIN);
    private static final Username CEDRIC = Username.of("cedric@" + DEFAULT_DOMAIN);
    private static final boolean COLLECTED_CONTACT_EXISTS = true;

    @RegisterExtension
    static WireMockOpenPaaSServerExtension openPaasServerExtension = new WireMockOpenPaaSServerExtension();

    @RegisterExtension
    static DavServerExtension davServerExtension = new DavServerExtension();

    private TemporaryJamesServer jamesServer;

    @RegisterExtension
    public SMTPMessageSender messageSender = new SMTPMessageSender(DEFAULT_DOMAIN);

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    @BeforeEach
    void setup(@TempDir File temporaryFolder) throws Exception {
        jamesServer = TemporaryJamesServer.builder()
            .withBase(Modules.combine(MemoryJamesServerMain.SMTP_AND_IMAP_MODULE,
                new OpenPaasModule()))
            .withOverrides(new InMemoryEmailAddressContactSearchEngineModule())
            .withOverrides(new AbstractModule() {
                @Override
                protected void configure() {
                    install(new OpenPaasModule.DavModule());
                    install(new CollectTrustedContactsListenerModule());
                }

                @Provides
                @Singleton
                public OpenPaasConfiguration provideOpenPaasConfiguration() {
                    return new OpenPaasConfiguration(
                        openPaasServerExtension.getBaseUrl(),
                        WireMockOpenPaaSServerExtension.ALICE_ID.value(),
                        WireMockOpenPaaSServerExtension.GOOD_PASSWORD,
                        false,
                        davServerExtension.getDavConfiguration());
                }

                @ProvidesIntoSet
                public BlobResolver provideFakeBlobResolver() {
                    return (blobId, mailboxSession) -> Mono.empty();
                }
            })
            .withMailetContainer(TemporaryJamesServer.defaultMailetContainerConfiguration()
                .putProcessor(ProcessorConfiguration.transport()
                    .addMailet(MailetConfiguration.builder()
                        .matcher(All.class)
                        .mailet(CardDavCollectedContact.class))
                    .addMailetsFrom(CommonProcessors.deliverOnlyTransport()))
            )
            .build(temporaryFolder);

        jamesServer.start();
        DataProbe dataProbe = jamesServer.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DEFAULT_DOMAIN);
        dataProbe.addUser(ALICE.asString(), PASSWORD);
        dataProbe.addUser(BOB.asString(), PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), PASSWORD);
    }

    @AfterEach
    void tearDown() {
        jamesServer.shutdown();
    }

    @Test
    void shouldPUTCreateCollectedContactWhenContactDoesNotExist() throws Exception {
        // Setup mock server
        DavUid contactUid = CardDavUtils.createContactUid(BOB.asMailAddress());
        OpenPaaSUserId openPassUid = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(ALICE, openPassUid);
        // Contact does not exist
        davServerExtension.setCollectedContactExists(ALICE, openPassUid, contactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, openPassUid, contactUid);

        // when alice sends an email to bob
        aliceSendAnEmailToBob();

        // then the endpoint createCollectedContact is called
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, openPassUid, contactUid, 1);
    }

    @Test
    void shouldNotPUTCreateCollectedContactWhenContactExists() throws Exception {
        // Setup mock server
        DavUid contactUid = CardDavUtils.createContactUid(BOB.asMailAddress());
        OpenPaaSUserId openPassUid = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(ALICE, openPassUid);
        // Contact exists
        davServerExtension.setCollectedContactExists(ALICE, openPassUid, contactUid, COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, openPassUid, contactUid);

        // when alice sends an email to bob
        aliceSendAnEmailToBob();

        // then the endpoint createCollectedContact is not called
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, openPassUid, contactUid, 0);
    }

    @Test
    void shouldNotPUTCreateCollectedContactWhenSearchEmailDoesNotExist() throws Exception {
        // Setup mock server
        DavUid contactUid = CardDavUtils.createContactUid(BOB.asMailAddress());
        OpenPaaSUserId openPassUid = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailNotFound(ALICE);
        davServerExtension.setCollectedContactExists(ALICE, openPassUid, contactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, openPassUid, contactUid);

        // when alice sends an email to bob
        aliceSendAnEmailToBob();

        davServerExtension.assertCollectedContactExistsWasCalled(ALICE, openPassUid, contactUid, 0);
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, openPassUid, contactUid, 0);
    }

    @Test
    void shouldPUTCreateCollectedContactMultipleTimesWhenMultipleRecipients() throws Exception {
        // Setup mock server
        DavUid bobContactUid = CardDavUtils.createContactUid(BOB.asMailAddress());
        DavUid cedricContactUid = CardDavUtils.createContactUid(CEDRIC.asMailAddress());
        OpenPaaSUserId aliceOpenPassId = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(ALICE, aliceOpenPassId);
        davServerExtension.setCollectedContactExists(ALICE, aliceOpenPassId, bobContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, aliceOpenPassId, bobContactUid);
        davServerExtension.setCollectedContactExists(ALICE, aliceOpenPassId, cedricContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, aliceOpenPassId, cedricContactUid);

        // when alice sends an email to bob and cedric
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(ALICE.asString())
                    .addToRecipient(BOB.asString())
                    .addToRecipient(CEDRIC.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(ALICE.asString())
                .recipients(BOB.asString(), CEDRIC.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // then the endpoint createCollectedContact is called twice
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, aliceOpenPassId, bobContactUid, 1);
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, aliceOpenPassId, cedricContactUid, 1);
    }

    @Test
    void shouldPUTCreateCollectedContactForContactDoesNotExistWhenMultipleRecipients() throws Exception {
        // Setup mock server
        DavUid bobContactUid = CardDavUtils.createContactUid(BOB.asMailAddress());
        DavUid cedricContactUid = CardDavUtils.createContactUid(CEDRIC.asMailAddress());
        OpenPaaSUserId aliceOpenPassId = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(ALICE, aliceOpenPassId);
        davServerExtension.setCollectedContactExists(ALICE, aliceOpenPassId, bobContactUid, COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, aliceOpenPassId, bobContactUid);
        davServerExtension.setCollectedContactExists(ALICE, aliceOpenPassId, cedricContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, aliceOpenPassId, cedricContactUid);

        // when alice sends an email to bob and cedric
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(ALICE.asString())
                    .addToRecipient(BOB.asString())
                    .addToRecipient(CEDRIC.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(ALICE.asString())
                .recipients(BOB.asString(), CEDRIC.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(CEDRIC, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // then the endpoint createCollectedContact is called once
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, aliceOpenPassId, bobContactUid, 0);
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, aliceOpenPassId, cedricContactUid, 1);
    }

    @Test
    void aliceShouldHaveCollectedContactAddedWhenAliceUsesAliasToSendMail() throws Exception {
        // add an alias for Alice
        String aliceAlias = "alice-alias@" + DEFAULT_DOMAIN;
        jamesServer.getProbe(DataProbeImpl.class)
            .addUserAliasMapping(ALICE.getLocalPart(), DEFAULT_DOMAIN, aliceAlias);

        // Setup mock server
        DavUid bobContactUid = CardDavUtils.createContactUid(BOB.asMailAddress());
        DavUid cedricContactUid = CardDavUtils.createContactUid(CEDRIC.asMailAddress());
        OpenPaaSUserId aliceOpenPassId = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(ALICE, aliceOpenPassId);
        davServerExtension.setCollectedContactExists(ALICE, aliceOpenPassId, bobContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, aliceOpenPassId, bobContactUid);
        davServerExtension.setCollectedContactExists(ALICE, aliceOpenPassId, cedricContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(ALICE, aliceOpenPassId, cedricContactUid);

        // when Alice uses her alias to email Bob
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(aliceAlias)
                    .addToRecipient(BOB.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(aliceAlias)
                .recipients(BOB.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        // then Alice should have a collected contact created for BOB
        davServerExtension.assertCreateCollectedContactWasCalled(ALICE, aliceOpenPassId, bobContactUid, 1);
    }

    @Test
    void shouldCollectContactsWhenAddedEventContainsToBeCollectedFlag() throws Exception {
        DavUid aliceContactUid = CardDavUtils.createContactUid(ALICE.asMailAddress());
        OpenPaaSUserId bobOpenPassId = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(BOB, bobOpenPassId);
        davServerExtension.setCollectedContactExists(BOB, bobOpenPassId, aliceContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(BOB, bobOpenPassId, aliceContactUid);

        appendMessageToInbox(BOB, MessageManager.AppendCommand.builder()
            .withFlags(new Flags(TO_BE_COLLECTED_FLAG))
            .build(Message.Builder.of()
                .setFrom(ALICE.asString())
                .setTo(BOB.asString())
                .setSubject("trusted contacts")
                .setBody("Body", StandardCharsets.UTF_8)
                .build()));

        awaitAtMostOneMinute.untilAsserted(() ->
            davServerExtension.assertCreateCollectedContactWasCalled(BOB, bobOpenPassId, aliceContactUid, 1));
    }

    @Test
    void shouldCollectContactsWhenFlagsUpdatedAddsToBeCollectedFlag() throws Exception {
        DavUid aliceContactUid = CardDavUtils.createContactUid(ALICE.asMailAddress());
        OpenPaaSUserId bobOpenPassId = new OpenPaaSUserId(UUID.randomUUID().toString());
        openPaasServerExtension.setSearchEmailExist(BOB, bobOpenPassId);
        davServerExtension.setCollectedContactExists(BOB, bobOpenPassId, aliceContactUid, !COLLECTED_CONTACT_EXISTS);
        davServerExtension.setCreateCollectedContact(BOB, bobOpenPassId, aliceContactUid);

        ComposedMessageId composedMessageId = appendMessageToInbox(BOB, MessageManager.AppendCommand.builder()
            .withFlags(new Flags())
            .build(Message.Builder.of()
                .setFrom(ALICE.asString())
                .setTo(BOB.asString())
                .setSubject("trusted contacts")
                .setBody("Body", StandardCharsets.UTF_8)
                .build()));

        jamesServer.getProbe(MailboxProbeImpl.class)
            .setFlags(BOB, MailboxPath.inbox(BOB), composedMessageId.getUid(), new Flags(TO_BE_COLLECTED_FLAG));

        awaitAtMostOneMinute.untilAsserted(() ->
            davServerExtension.assertCreateCollectedContactWasCalled(BOB, bobOpenPassId, aliceContactUid, 1));
    }

    private void aliceSendAnEmailToBob() throws MessagingException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidKeySpecException {
        messageSender.connect(LOCALHOST_IP, jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), PASSWORD)
            .sendMessage(FakeMail.builder()
                .name("name")
                .mimeMessage(MimeMessageBuilder.mimeMessageBuilder()
                    .setSender(ALICE.asString())
                    .addToRecipient(BOB.asString())
                    .setSubject("Contact collection Rocks")
                    .setText("This is my email"))
                .sender(ALICE.asString())
                .recipients(BOB.asString()));

        testIMAPClient.connect(LOCALHOST_IP, jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);
    }

    private ComposedMessageId appendMessageToInbox(Username recipient, MessageManager.AppendCommand appendCommand) throws Exception {
        MailboxProbeImpl mailboxProbe = jamesServer.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox("#private", recipient.asString(), DefaultMailboxes.INBOX);
        return mailboxProbe
            .appendMessage(
                recipient.asString(),
                MailboxPath.inbox(recipient),
                appendCommand);
    }
}
