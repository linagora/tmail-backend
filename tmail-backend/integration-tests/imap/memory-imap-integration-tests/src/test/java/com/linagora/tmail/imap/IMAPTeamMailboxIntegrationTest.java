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

package com.linagora.tmail.imap;

import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.ACLProbeImpl;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxProbe;

import scala.jdk.javaapi.CollectionConverters;


public class IMAPTeamMailboxIntegrationTest {
    static final String DOMAIN = "domain.tld";
    static final Username MINISTER = Username.of("minister@" + DOMAIN);
    static final Username SECRETARY = Username.of("secretary@" + DOMAIN);
    static final Username OTHER3 = Username.of("other3@" + DOMAIN);
    static final String MINISTER_PASSWORD = "secret";
    static final String SECRETARY_PASSWORD = "secret";
    static final String OTHER3_PASSWORD = "secret";
    static final String IMAP_HOST = "127.0.0.1";
    static int imapPort;
    static final TeamMailbox MARKETING_TEAM_MAILBOX = TeamMailbox.apply(Domain.of(DOMAIN), TeamMailboxName.fromString("marketing").toOption().get());
    static final TeamMailbox SALE_TEAM_MAILBOX = TeamMailbox.apply(Domain.of(DOMAIN), TeamMailboxName.fromString("sale").toOption().get());

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .mailbox(new MailboxConfiguration(false))
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(TeamMailboxProbe.class)))
        .build();

    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();

    @BeforeEach
    void setup(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(MINISTER.asString(), MINISTER_PASSWORD)
            .addUser(SECRETARY.asString(), SECRETARY_PASSWORD)
            .addUser(OTHER3.asString(), OTHER3_PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(MINISTER));
        mailboxProbe.createMailbox(MailboxPath.forUser(MINISTER, "Sent"));
        mailboxProbe.createMailbox(MailboxPath.forUser(MINISTER, "Outbox"));
        mailboxProbe.createMailbox(MailboxPath.inbox(SECRETARY));
        mailboxProbe.createMailbox(MailboxPath.inbox(OTHER3));

        mailboxProbe.appendMessage(MINISTER.asString(),
            MailboxPath.inbox(MINISTER),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("minister message subject")
                .setBody("minister message body content 123", StandardCharsets.UTF_8)
                .build()));

        server.getProbe(DataProbeImpl.class)
            .addAuthorizedUser(MINISTER, SECRETARY);

        server.getProbe(TeamMailboxProbe.class)
            .create(MARKETING_TEAM_MAILBOX)
            .create(SALE_TEAM_MAILBOX)
            .addMember(MARKETING_TEAM_MAILBOX, MINISTER)
            .addMember(MARKETING_TEAM_MAILBOX, SECRETARY)
            .addMember(SALE_TEAM_MAILBOX, MINISTER);

        server.getProbe(ACLProbeImpl.class)
            .replaceRights(MailboxPath.inbox(OTHER3), MINISTER.asString(), MailboxACL.Rfc4314Rights.of(List.of(MailboxACL.Right.Administer, MailboxACL.Right.Lookup,
                MailboxACL.Right.Read)));

        mailboxProbe.appendMessage(MINISTER.asString(), MARKETING_TEAM_MAILBOX.mailboxPath("INBOX"),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Mail in marketing team mailbox")
                .setBody("This is content of teammailbox", StandardCharsets.UTF_8)
                .build()));

        imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Test
    void namespaceShouldReturnTeamMailboxNameSpace() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("NAMESPACE"))
            .contains("NAMESPACE ((\"\" \".\")) ((\"#user.\" \".\")) ((\"#TeamMailbox.\" \".\"))");
    }

    @Test
    void listShouldReturnAllTeamMailboxesWhenQueryAll() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"")
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.marketing\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Trash\"")
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.sale\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Trash");
    }

    @Test
    void listShouldReturnAllTeamMailboxWhenTeamMailboxNamespace() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#TeamMailbox\" \"*\""))
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.marketing\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Trash\"")
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.sale\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Trash")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"INBOX")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"");
    }

    @Test
    void listShouldNotReturnPrivateMailboxWhenTeamMailboxNamespace() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#TeamMailbox\" \"*\""))
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"INBOX\"");
    }

    @Test
    void listShouldFilterTeamMailboxNameWhenProvideMailboxPathNamePart() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#TeamMailbox.marketing\" \"*\""))
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.marketing\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Trash\"")
            .doesNotContain("* LIST (\\HasChildren) \".\" \"#TeamMailbox.sale\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Drafts\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.INBOX\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Outbox\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Sent\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Trash")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"INBOX")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"");
    }

    @Test
    void listShouldFilterTeamMailboxWhenMailboxNameWithRegexParameterIsProvided() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"\" \"#TeamMailbox.*\""))
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.marketing\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Trash\"")
            .contains("* LIST (\\HasChildren) \".\" \"#TeamMailbox.sale\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Trash")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"INBOX")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"");
    }

    @Test
    void listShouldFilterTeamMailboxWhenSUBSCRIBEDOptionANDMailboxNameWithRegexParameterIsProvided() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST (SUBSCRIBED) \"\" \"#TeamMailbox.*\""))
            .contains("* LIST (\\HasChildren \\Subscribed) \".\" \"#TeamMailbox.marketing\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.marketing.Drafts\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.marketing.Sent\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.marketing.Trash\"")
            .contains("* LIST (\\HasChildren \\Subscribed) \".\" \"#TeamMailbox.sale\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.sale.Drafts\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.sale.INBOX\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.sale.Outbox\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.sale.Sent\"")
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#TeamMailbox.sale.Trash")
            .doesNotContain("\"INBOX\"")
            .doesNotContain("\"#user.other3.INBOX\"");
    }

    @Test
    void listShouldFilterTeamMailboxWhenReferenceNameAndMailboxNameParameterWithRegexIsProvided() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#TeamMailbox\" \"marketing.*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Drafts\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Sent\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Trash\"")
            .doesNotContain("* LIST (\\HasChildren) \".\" \"#TeamMailbox.sale\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Drafts\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.INBOX\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Outbox\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Sent\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.Trash")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"INBOX")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"");
    }

    @Test
    void listShouldFilterTeamMailboxWhenReferenceNameAndAbsoluteMailboxNameParameterIsProvided() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#TeamMailbox\" \"marketing.INBOX\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.Outbox\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.sale.INBOX\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"INBOX")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"");
    }

    @Test
    void statusOnTeamMailboxShouldReturnStatus() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("STATUS \"#TeamMailbox.marketing\" (MESSAGES)"))
            .contains("* STATUS \"#TeamMailbox.marketing\" (MESSAGES 0)");
    }

    @Test
    void statusOnTeamMailboxInboxShouldReturnMessagesCounterWhenRequest() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("STATUS \"#TeamMailbox.marketing.INBOX\" (MESSAGES)"))
            .contains("* STATUS \"#TeamMailbox.marketing.INBOX\" (MESSAGES 1)");
    }

    @Test
    void listShouldNotReturnTeamMailboxWhenQueryUser() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#user\" \"marketing.INBOX\""))
            .doesNotContain("#TeamMailbox");
    }

    @Test
    void listShouldReturnUserMailboxWhenQueryUserNamespaceInReferenceNameArgument() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"#user\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"");
    }

    @Test
    void listShouldReturnUserMailboxWhenQueryUserNamespaceInMailboxNameArgument() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("LIST \"\" \"#user.*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#user.other3.INBOX\"")
            .doesNotContain("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.INBOX\"");
    }

    @Test
    void listShouldReturnUserMailboxWhenSUBSCRIBEDOptionAndQueryUserNamespaceInMailboxNameArgument() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD);

        imapClient.sendCommand("SUBSCRIBE \"Mailbox123\"");
        imapClient.sendCommand("SUBSCRIBE \"#user.other3.INBOX\"");
        imapClient.sendCommand("SUBSCRIBE \"#TeamMailbox.marketing.INBOX\"");

        assertThat(imapClient
            .sendCommand("LIST (SUBSCRIBED) \"\" \"#user.*\""))
            .contains("* LIST (\\HasNoChildren \\Subscribed) \".\" \"#user.other3.INBOX\"")
            .doesNotContain("\"#TeamMailbox.marketing.INBOX\"")
            .doesNotContain("\"Mailbox123\"");
    }

    @Test
    void memberCanCreateTopFolder() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD);

        // Verify that the team mailbox `marketing.new1` does not exist
        assertThat(imapClient
            .sendCommand("LIST \"\" \"*\""))
            .doesNotContain("\"#TeamMailbox.marketing.new1\"");

        // Create the team mailbox `marketing.new1` successfully
        assertThat(imapClient
            .sendCommand("CREATE #TeamMailbox.marketing.new1"))
            .contains("CREATE completed");

        // Verify that the team mailbox `marketing.new1` exists
        assertThat(imapClient
            .sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.new1\"");
    }

    @Test
    void topFolderIsCreatedByMemberCanAppendMessagesSuccessful(GuiceJamesServer server) throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD);

        // Create the team mailbox `marketing.new1` successfully
        String folderName = UUID.randomUUID().toString();
        assertThat(imapClient
            .sendCommand("CREATE #TeamMailbox.marketing." + folderName))
            .contains("CREATE completed");

        server.getProbe(MailboxProbeImpl.class).appendMessage(MINISTER.asString(), MARKETING_TEAM_MAILBOX.mailboxPath(folderName),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Mail in marketing team mailbox" + folderName)
                .setBody("This is content of teammailbox", StandardCharsets.UTF_8)
                .build()));

        Thread.sleep(200);

        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("""
                STATUS "#TeamMailbox.marketing.%s" (MESSAGES)
                """.formatted(folderName)))
            .contains("""
                * STATUS "#TeamMailbox.marketing.%s" (MESSAGES 1)
                """.formatted(folderName).trim());
    }

    @Test
    void topFolderCreatedByMemberAShouldBeAccessibleByOtherMemberB() throws Exception {
        // Alice: Verify that the team mailbox `marketing.new1` does not exist
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(SECRETARY, SECRETARY_PASSWORD)
            .sendCommand("LIST \"\" \"*\""))
            .doesNotContain("\"#TeamMailbox.marketing.new1\"");

        // Bob: Create the team mailbox `marketing.new1` successfully
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("CREATE #TeamMailbox.marketing.new1"))
            .contains("CREATE completed");

        // Alice: Verify that the team mailbox `marketing.new1` exists
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(SECRETARY, SECRETARY_PASSWORD)
            .sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.new1\"");
    }

    @Test
    void topFolderCreatedByMemberAShouldBeAccessibleByOtherUserWhenHasRight(GuiceJamesServer server) throws Exception {
        // Verify user `other3` is not a member of the team mailbox `marketing`
        assertThat(CollectionConverters.asJava(server.getProbe(TeamMailboxProbe.class)
            .listMembers(MARKETING_TEAM_MAILBOX)))
            .doesNotContain(OTHER3)
            .contains(MINISTER);

        // Verify that the team mailbox `marketing.new1` does not list in the mailbox list of user `other3`
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(OTHER3, OTHER3_PASSWORD)
            .sendCommand("LIST \"\" \"*\""))
            .doesNotContain("\"#TeamMailbox.marketing.new1\"");

        // Given `minister` creates the team mailbox `marketing.new1`
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(MINISTER, MINISTER_PASSWORD)
            .sendCommand("CREATE #TeamMailbox.marketing.new1"))
            .contains("CREATE completed");

        // When add the right `other3` to the team mailbox `marketing.new1`
        server.getProbe(ACLProbeImpl.class)
            .replaceRights(MARKETING_TEAM_MAILBOX.mailboxPath("new1"),
                OTHER3.asString(),
                MailboxACL.Rfc4314Rights.of(List.of(MailboxACL.Right.Lookup, MailboxACL.Right.Read)));

        // Then the team mailbox `marketing.new1` should list in the mailbox list of user `other3`
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(OTHER3, OTHER3_PASSWORD)
            .sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"#TeamMailbox.marketing.new1\"");
    }
}
