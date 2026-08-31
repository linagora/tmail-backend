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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.net.MalformedServerReplyException;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxProbe;

class IMAPTeamMailboxMigrationIntegrationTest {
    static final String DOMAIN = "domain.tld";
    static final Username MINISTER = Username.of("minister@" + DOMAIN);
    static final Username OTHER = Username.of("other@" + DOMAIN);
    static final Username MANAGER = Username.of("manager@" + DOMAIN);
    static final Username ADMIN = Username.of("admin@" + DOMAIN);
    static final Username IMAP_ADMIN = Username.of("imapadmin@" + DOMAIN);
    static final String MINISTER_PASSWORD = "secret";
    static final String OTHER_PASSWORD = "secret";
    static final String MANAGER_PASSWORD = "secret";
    static final String ADMIN_PASSWORD = "secret";
    static final String IMAP_ADMIN_PASSWORD = "secret";
    static final String IMAP_HOST = "127.0.0.1";
    static int imapPort;
    static final Pattern MY_RIGHTS_RESPONSE = Pattern.compile("\\* MYRIGHTS \"[^\"]*\" \"([^\"]*)\"");
    static final TeamMailbox MARKETING_TEAM_MAILBOX = TeamMailbox.apply(Domain.of(DOMAIN),
        TeamMailboxName.fromString("marketing").toOption().get());

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
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
            .addUser(OTHER.asString(), OTHER_PASSWORD)
            .addUser(MANAGER.asString(), MANAGER_PASSWORD)
            .addUser(ADMIN.asString(), ADMIN_PASSWORD)
            .addUser(IMAP_ADMIN.asString(), IMAP_ADMIN_PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(MINISTER));

        server.getProbe(TeamMailboxProbe.class)
            .create(MARKETING_TEAM_MAILBOX)
            .addMember(MARKETING_TEAM_MAILBOX, MINISTER)
            .addManager(MARKETING_TEAM_MAILBOX, MANAGER);

        mailboxProbe.appendMessage(MINISTER.asString(), MARKETING_TEAM_MAILBOX.mailboxPath("INBOX"),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("Mail in marketing team mailbox")
                .setBody("This is content of the team mailbox", StandardCharsets.UTF_8)
                .build()));

        imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    private Username scopedLogin(Username member) {
        return Username.fromLocalPartWithDomain(member.getLocalPart() + "+marketing", DOMAIN);
    }

    /**
     * The rights the scoped session holds on the designated team mailbox folder, as reported by MYRIGHTS.
     */
    private String myRights(TestIMAPClient imapClient, String mailbox) throws IOException {
        String response = imapClient.sendCommand("MYRIGHTS \"" + mailbox + "\"");
        Matcher matcher = MY_RIGHTS_RESPONSE.matcher(response);
        assertThat(matcher.find()).describedAs("MYRIGHTS untagged response in [%s]", response).isTrue();
        return matcher.group(1);
    }

    private Username delegatedLogin(Username delegate, String authorizationLocalPart) {
        return Username.fromLocalPartWithDomain(delegate.getLocalPart() + "+" + authorizationLocalPart, DOMAIN);
    }

    @Test
    void memberShouldLoginWhenScopedToTeamMailbox() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD)
            .sendCommand("NOOP"))
            .contains("OK NOOP completed");
    }

    @Test
    void listShouldPresentTeamMailboxLayoutAsRoot() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD)
            .sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"INBOX\"")
            .contains("* LIST (\\HasNoChildren) \".\" \"Sent\"")
            .doesNotContain("#TeamMailbox");
    }

    @Test
    void selectAndFetchShouldReturnTeamMailboxMessage() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);

        assertThat(imapClient.sendCommand("SELECT INBOX"))
            .contains("SELECT completed");

        assertThat(imapClient.sendCommand("FETCH 1 BODY[TEXT]"))
            .contains("This is content of the team mailbox");
    }

    @Test
    void listShouldMatchTheReferenceNameItself() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD)
            .sendCommand("LIST \"Sent\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"Sent\"");
    }

    @Test
    void selectShouldBeCaseInsensitiveOnInbox() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD)
            .sendCommand("SELECT inbox"))
            .contains("SELECT completed");
    }

    @Test
    void statusShouldReturnTeamMailboxInboxCounter() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD)
            .sendCommand("STATUS \"INBOX\" (MESSAGES)"))
            .contains("* STATUS \"INBOX\" (MESSAGES 1)");
    }

    @Test
    void namespaceShouldNotLeakTeamMailboxPrefix() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD)
            .sendCommand("NAMESPACE"))
            .doesNotContain("#TeamMailbox")
            .doesNotContain("#private");
    }

    @Test
    void storeShouldUpdateTeamMailboxMessageFlags() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);
        imapClient.sendCommand("SELECT INBOX");

        assertThat(imapClient.sendCommand("STORE 1 +FLAGS (\\Seen)"))
            .contains("\\Seen")
            .contains("STORE completed");
    }

    @Test
    void copyShouldTargetTeamMailboxSubtree() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);
        imapClient.sendCommand("SELECT INBOX");

        assertThat(imapClient.sendCommand("COPY 1 Sent"))
            .contains("COPY completed");
        assertThat(imapClient.sendCommand("STATUS \"Sent\" (MESSAGES)"))
            .contains("* STATUS \"Sent\" (MESSAGES 1)");
    }

    @Test
    void moveShouldTargetTeamMailboxSubtree() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);
        imapClient.sendCommand("SELECT INBOX");

        assertThat(imapClient.sendCommand("MOVE 1 Sent"))
            .contains("MOVE completed");
        assertThat(imapClient.sendCommand("STATUS \"Sent\" (MESSAGES)"))
            .contains("* STATUS \"Sent\" (MESSAGES 1)");
        assertThat(imapClient.sendCommand("STATUS \"INBOX\" (MESSAGES)"))
            .contains("* STATUS \"INBOX\" (MESSAGES 0)");
    }

    @Test
    void expungeShouldOperateOnTeamMailbox() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);
        imapClient.sendCommand("SELECT INBOX");
        imapClient.sendCommand("STORE 1 +FLAGS (\\Deleted)");

        assertThat(imapClient.sendCommand("EXPUNGE"))
            .contains("EXPUNGE completed");
        assertThat(imapClient.sendCommand("STATUS \"INBOX\" (MESSAGES)"))
            .contains("* STATUS \"INBOX\" (MESSAGES 0)");
    }

    @Test
    void nonMemberShouldFailToLoginScopedToTeamMailbox() {
        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(OTHER), OTHER_PASSWORD))
            .hasMessage("Login failed");
    }

    @Test
    void adminShouldLoginWhenScopedToTeamMailboxHeIsNotMemberOf() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(ADMIN), ADMIN_PASSWORD)
            .sendCommand("NOOP"))
            .contains("OK NOOP completed");
    }

    @Test
    void adminShouldAccessTeamMailboxMessageWhenScoped() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(ADMIN), ADMIN_PASSWORD);

        assertThat(imapClient.sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"INBOX\"")
            .doesNotContain("#TeamMailbox");
        assertThat(imapClient.sendCommand("SELECT INBOX"))
            .contains("SELECT completed");
        assertThat(imapClient.sendCommand("FETCH 1 BODY[TEXT]"))
            .contains("This is content of the team mailbox");
    }

    @Test
    void adminShouldKeepDelegatingOntoRegularUserWhoseNameParsesAsATeamMailboxName() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(delegatedLogin(ADMIN, OTHER.getLocalPart()), ADMIN_PASSWORD);

        assertThat(imapClient.sendCommand("STATUS \"INBOX\" (MESSAGES)"))
            .contains("* STATUS \"INBOX\" (MESSAGES 0)");
    }

    @Test
    void imapConfiguredAdminShouldLoginWhenScopedToTeamMailboxHeIsNotMemberOf() throws Exception {
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(IMAP_ADMIN), IMAP_ADMIN_PASSWORD)
            .sendCommand("NOOP"))
            .contains("OK NOOP completed");
    }

    @Test
    void imapConfiguredAdminShouldAccessTeamMailboxMessageWhenScoped() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(IMAP_ADMIN), IMAP_ADMIN_PASSWORD);

        assertThat(imapClient.sendCommand("LIST \"\" \"*\""))
            .contains("* LIST (\\HasNoChildren) \".\" \"INBOX\"")
            .doesNotContain("#TeamMailbox");
        assertThat(imapClient.sendCommand("SELECT INBOX"))
            .contains("SELECT completed");
        assertThat(imapClient.sendCommand("FETCH 1 BODY[TEXT]"))
            .contains("This is content of the team mailbox");
    }

    @Test
    void memberShouldNotBeGrantedAdministerRightOnTeamMailbox() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);

        assertThat(myRights(imapClient, "INBOX")).doesNotContain("a");
    }

    @Test
    void memberShouldNotBeAllowedToSetAclOnTeamMailbox() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MINISTER), MINISTER_PASSWORD);

        assertThatThrownBy(() -> imapClient.sendCommand("SETACL \"INBOX\" \"" + OTHER.asString() + "\" lr"))
            .isInstanceOf(MalformedServerReplyException.class)
            .hasMessageContaining("NO SETACL You need the Administer right to perform command SETACL on mailbox INBOX.");
    }

    @Test
    void managerShouldBeGrantedAdministerRightOnTeamMailbox() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MANAGER), MANAGER_PASSWORD);

        assertThat(myRights(imapClient, "INBOX")).contains("a");
    }

    @Test
    void managerShouldBeAllowedToSetAclOnTeamMailbox() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(MANAGER), MANAGER_PASSWORD);

        assertThat(imapClient.sendCommand("SETACL \"INBOX\" \"" + OTHER.asString() + "\" lr"))
            .contains("OK SETACL completed");
        assertThat(imapClient.sendCommand("GETACL \"INBOX\""))
            .contains("\"" + OTHER.asString() + "\" \"lr\"");
    }

    @Test
    void adminShouldBeGrantedAdministerRightOnTeamMailboxHeIsNotMemberOf() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(ADMIN), ADMIN_PASSWORD);

        assertThat(myRights(imapClient, "INBOX")).contains("a");
    }

    @Test
    void adminShouldBeAllowedToSetAclOnTeamMailboxHeIsNotMemberOf() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(ADMIN), ADMIN_PASSWORD);

        assertThat(imapClient.sendCommand("SETACL \"INBOX\" \"" + OTHER.asString() + "\" lr"))
            .contains("OK SETACL completed");
        assertThat(imapClient.sendCommand("GETACL \"INBOX\""))
            .contains("\"" + OTHER.asString() + "\" \"lr\"");
    }

    @Test
    void imapConfiguredAdminShouldBeGrantedAdministerRightOnTeamMailboxHeIsNotMemberOf() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(IMAP_ADMIN), IMAP_ADMIN_PASSWORD);

        assertThat(myRights(imapClient, "INBOX")).contains("a");
    }

    @Test
    void imapConfiguredAdminShouldBeAllowedToSetAclOnTeamMailboxHeIsNotMemberOf() throws Exception {
        TestIMAPClient imapClient = testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(scopedLogin(IMAP_ADMIN), IMAP_ADMIN_PASSWORD);

        assertThat(imapClient.sendCommand("SETACL \"INBOX\" \"" + OTHER.asString() + "\" lr"))
            .contains("OK SETACL completed");
        assertThat(imapClient.sendCommand("GETACL \"INBOX\""))
            .contains("\"" + OTHER.asString() + "\" \"lr\"");
    }
}
