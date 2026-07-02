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

package com.linagora.tmail.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.mock.smtp.server.model.Mail;
import org.apache.james.mock.smtp.server.testing.MockSmtpServerExtension;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.migration.postgres.PostgresMigratedUsersDAO;

/**
 * Boots the migration proxy MTA and checks that SMTP mail is routed per recipient: a migrated
 * recipient reaches the "new" backend, a non-migrated local recipient reaches the "old" backend. Both
 * backends are James MockSMTP servers; the proxy's RemoteDelivery gateways are pointed at them.
 */
class SmtpRoutingTest {
    private static final String DOMAIN = "managed.tld";
    private static final String MIGRATED_RECIPIENT = "newuser@" + DOMAIN;
    private static final String LEGACY_RECIPIENT = "olduser@" + DOMAIN;
    private static final String SENDER = "sender@" + DOMAIN;
    private static final int RELAY_SMTP_PORT = 10026;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();
    @RegisterExtension
    static MockSmtpServerExtension oldBackend = new MockSmtpServerExtension();
    @RegisterExtension
    static MockSmtpServerExtension newBackend = new MockSmtpServerExtension();

    @TempDir
    static File workingDirectory;

    private static GuiceJamesServer server;

    @BeforeAll
    static void setUp() throws Exception {
        // Point the RemoteDelivery gateways (interpolated in mailetcontainer.xml) at the mock backends.
        System.setProperty("migration.smtp.old.host", oldBackend.getMockSmtp().getIPAddress());
        System.setProperty("migration.smtp.old.port", "25");
        System.setProperty("migration.smtp.new.host", newBackend.getMockSmtp().getIPAddress());
        System.setProperty("migration.smtp.new.port", "25");

        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();
        server = MigrationProxyServer.createServer(configuration)
            .overrideWith(postgresExtension.getModule());
        server.start();

        server.getProbe(DataProbeImpl.class).fluent().addDomain(DOMAIN);
        new PostgresMigratedUsersDAO(postgresExtension.getDefaultPostgresExecutor())
            .insert(Username.of(MIGRATED_RECIPIENT))
            .block();
    }

    @AfterAll
    static void tearDown() {
        if (server != null) {
            server.stop();
        }
        System.clearProperty("migration.smtp.old.host");
        System.clearProperty("migration.smtp.old.port");
        System.clearProperty("migration.smtp.new.host");
        System.clearProperty("migration.smtp.new.port");
    }

    @Test
    void migratedRecipientShouldReachNewBackendAndLegacyShouldReachOld() throws Exception {
        try (SMTPMessageSender sender = SMTPMessageSender.noAuthentication("127.0.0.1", RELAY_SMTP_PORT, DOMAIN)) {
            sender.sendMessage(SENDER, MIGRATED_RECIPIENT);
            sender.sendMessage(SENDER, LEGACY_RECIPIENT);
        }

        await().atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> {
                assertThat(receivedRecipients(newBackend)).contains(MIGRATED_RECIPIENT);
                assertThat(receivedRecipients(oldBackend)).contains(LEGACY_RECIPIENT);
            });

        assertThat(receivedRecipients(newBackend)).doesNotContain(LEGACY_RECIPIENT);
        assertThat(receivedRecipients(oldBackend)).doesNotContain(MIGRATED_RECIPIENT);
    }

    private static List<String> receivedRecipients(MockSmtpServerExtension backend) {
        return backend.getMockSmtp().getConfigurationClient().listMails().stream()
            .map(Mail::getEnvelope)
            .flatMap(envelope -> envelope.getRecipients().stream())
            .map(recipient -> recipient.getAddress().asString())
            .toList();
    }
}
