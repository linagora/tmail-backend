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

import java.time.Duration;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.apache.james.events.EventListener;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.AllowCrossDomainAccessExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.TestIMAPClient;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.listener.EnrichACLListener;

/**
 * End to end validation of {@link EnrichACLListener}: IMAP SETACL designating a user by its sole local part
 * ends up granting rights to the fully qualified user of the mailbox owner domain.
 *
 * Virtual hosting is enabled and users are identified by their mail address (see usersrepository.xml).
 * Cross-domain sharing is allowed, otherwise SETACL refuses local-part-only entries upfront.
 */
class IMAPEnrichACLIntegrationTest {
    private static final String DOMAIN = "domain.tld";
    private static final Username BOB = Username.of("bob@" + DOMAIN);
    private static final Username ALICE = Username.of("alice@" + DOMAIN);
    private static final String PASSWORD = "secret";
    private static final String IMAP_HOST = "127.0.0.1";
    private static final String BOB_INBOX_SEEN_BY_ALICE = "#user.bob.INBOX";
    private static final ConditionFactory AWAIT = Awaitility.with()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofSeconds(10))
        .await();

    @RegisterExtension
    @Order(1)
    static AllowCrossDomainAccessExtension allowCrossDomainAccessExtension = new AllowCrossDomainAccessExtension();

    @RegisterExtension
    @Order(2)
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(binder -> Multibinder.newSetBinder(binder, EventListener.ReactiveGroupEventListener.class)
                .addBinding().to(EnrichACLListener.class)))
        .build();

    @RegisterExtension
    TestIMAPClient bobClient = new TestIMAPClient();

    @RegisterExtension
    TestIMAPClient aliceClient = new TestIMAPClient();

    private int imapPort;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), PASSWORD)
            .addUser(ALICE.asString(), PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.inbox(BOB));
        mailboxProbe.createMailbox(MailboxPath.inbox(ALICE));

        imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
    }

    @Test
    void setAclWithLocalPartOnlyShouldGrantRightsToTheUserOfTheOwnerDomain() throws Exception {
        TestIMAPClient bob = bobClient.connect(IMAP_HOST, imapPort).login(BOB, PASSWORD);

        assertThat(bob.sendCommand("SETACL INBOX alice lrs"))
            .contains("OK SETACL completed");

        AWAIT.untilAsserted(() -> assertThat(bob.sendCommand("GETACL INBOX"))
            .contains("* ACL \"INBOX\" \"alice@domain.tld\" \"lrs\"")
            .doesNotContain("\"alice\""));
    }

    @Test
    void userOfTheOwnerDomainShouldAccessTheMailboxSharedByLocalPartOnly() throws Exception {
        assertThat(bobClient.connect(IMAP_HOST, imapPort)
            .login(BOB, PASSWORD)
            .sendCommand("SETACL INBOX alice lrs"))
            .contains("OK SETACL completed");

        TestIMAPClient alice = aliceClient.connect(IMAP_HOST, imapPort).login(ALICE, PASSWORD);

        AWAIT.untilAsserted(() -> assertThat(alice.sendCommand("MYRIGHTS " + BOB_INBOX_SEEN_BY_ALICE))
            .contains("* MYRIGHTS \"" + BOB_INBOX_SEEN_BY_ALICE + "\" \"lrs\""));
        assertThat(alice.sendCommand("LIST \"\" \"*\""))
            .contains("\"" + BOB_INBOX_SEEN_BY_ALICE + "\"");
    }

    @Test
    void setAclWithLocalPartOnlyShouldMergeRightsIntoTheExistingQualifiedEntry() throws Exception {
        TestIMAPClient bob = bobClient.connect(IMAP_HOST, imapPort).login(BOB, PASSWORD);

        assertThat(bob.sendCommand("SETACL INBOX alice@domain.tld w"))
            .contains("OK SETACL completed");
        assertThat(bob.sendCommand("SETACL INBOX alice lr"))
            .contains("OK SETACL completed");

        AWAIT.untilAsserted(() -> assertThat(bob.sendCommand("GETACL INBOX"))
            .contains("* ACL \"INBOX\" \"alice@domain.tld\" \"lrw\"")
            .doesNotContain("\"alice\""));
    }

    @Test
    void setAclWithQualifiedUserShouldBeLeftUntouched() throws Exception {
        TestIMAPClient bob = bobClient.connect(IMAP_HOST, imapPort).login(BOB, PASSWORD);

        assertThat(bob.sendCommand("SETACL INBOX alice@domain.tld lrs"))
            .contains("OK SETACL completed");

        assertThat(bob.sendCommand("GETACL INBOX"))
            .contains("* ACL \"INBOX\" \"alice@domain.tld\" \"lrs\"");
    }
}
