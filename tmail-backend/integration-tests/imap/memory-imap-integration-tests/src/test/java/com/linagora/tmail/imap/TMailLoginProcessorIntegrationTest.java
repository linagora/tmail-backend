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
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
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

class TMailLoginProcessorIntegrationTest {

    static final Username MINISTER = Username.of("minister@" + DOMAIN);
    static final Username SECRETARY = Username.of("secretary@" + DOMAIN);
    static final Username OTHER3 = Username.of("other3@" + DOMAIN);
    static final String MINISTER_PASSWORD = "misecret";
    static final String SECRETARY_PASSWORD = "sesecret";
    static final String OTHER3_PASSWORD = "other3secret";
    static final String IMAP_HOST = "127.0.0.1";
    static int imapPort;

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(MailboxManagerClassProbe.class)))
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
        mailboxProbe.createMailbox(MailboxPath.inbox(SECRETARY));
        mailboxProbe.createMailbox(MailboxPath.inbox(OTHER3));

        mailboxProbe.appendMessage(MINISTER.asString(),
            MailboxPath.inbox(MINISTER),
            MessageManager.AppendCommand.from(Message.Builder.of()
                .setSubject("minister message subject")
                .setBody("minister message body content 123", StandardCharsets.UTF_8)
                .build()));
        Thread.sleep(500);

        // MINISTER delegate SECRETARY to access his mailbox
        server.getProbe(DataProbeImpl.class)
            .addAuthorizedUser(MINISTER, SECRETARY);

        imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Test
    void ministerShouldLoginSuccessfullyWithHisCredential() {
        assertThatCode(() ->
            testIMAPClient.connect(IMAP_HOST, imapPort)
                .login(MINISTER, MINISTER_PASSWORD)
                .select(INBOX))
            .doesNotThrowAnyException();
    }

    @Test
    void secretaryShouldLoginSuccessfullyWithHisCredential() {
        assertThatCode(() ->
            testIMAPClient.connect(IMAP_HOST, imapPort)
                .login(SECRETARY, SECRETARY_PASSWORD)
                .select(INBOX))
            .doesNotThrowAnyException();
    }

    @Test
    void secretaryCanAccessToMinisterWhenDelegate() throws Exception {
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("secretary+minister", DOMAIN);
        assertThat(testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, SECRETARY_PASSWORD)
            .select(INBOX)
            .readFirstMessage())
            .contains("minister message body content 123");
    }

    @Test
    void secretaryShouldLoginFailedWhenTryToAccessToOtherWithNoDelegation() {
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("secretary+other3", DOMAIN);

        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, SECRETARY_PASSWORD))
            .hasMessage("Login failed");
    }

    @Test
    void shouldLoginFailedWhenLocalPartInvalid() {
        // empty delegate user part
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("secretary+", DOMAIN);

        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, SECRETARY_PASSWORD))
            .hasMessage("Login failed");
    }

    @Test
    void secretaryShouldLoginFailedWhenTryToAccessToMinisterWithInvalidPassword() {
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("secretary+minister", DOMAIN);

        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, "invalidPassword"))
            .hasMessage("Login failed");
    }

    @Test
    void secretaryShouldLoginFailedWhenTryToAccessToMinisterWithInvalidDomain() {
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("secretary+minister", "invalidDomain");

        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, SECRETARY_PASSWORD))
            .hasMessage("Login failed");
    }

    @Test
    void secretaryShouldLoginFailedWhenTryToAccessToMinisterWithInvalidUser() {
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("invalidUser+minister", DOMAIN);

        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, SECRETARY_PASSWORD))
            .hasMessage("Login failed");
    }

    @Test
    void secretaryShouldLoginFailedWhenTryToAccessToMinisterWithInvalidDelegateUser() {
        Username secretaryDelegateUser = Username.fromLocalPartWithDomain("secretary+invalidUser", DOMAIN);

        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(secretaryDelegateUser, SECRETARY_PASSWORD))
            .hasMessage("Login failed");
    }

    @Test
    void secretaryShouldLoginFailedWhenInvalidCredential() {
        assertThatThrownBy(() -> testIMAPClient.connect(IMAP_HOST, imapPort)
            .login(SECRETARY, "invalidPass"))
            .hasMessage("Login failed");
    }
}
