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

import static com.linagora.tmail.migration.ProxySmtpClient.plainResponse;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;

/**
 * With {@code submission.auth.requireSSL}, credentials only ever transit an encrypted client connection.
 */
class SubmissionProxyRequireSslTest {
    private static final String BOB = "bob@managed.tld";
    private static final String PASSWORD = "secret";

    @RegisterExtension
    static PostgresExtension postgresExtension =
        PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    @TempDir
    static File workingDirectory;

    private static StubBackendServer oldBackend;
    private static GuiceJamesServer proxy;
    private static SubmissionProxyProbe probe;

    @BeforeAll
    static void setUpAll() throws Exception {
        oldBackend = new StubBackendServer("220 old.backend ESMTP")
            .reply("EHLO", "250 old.backend")
            .reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD), "235 2.7.0 Authentication successful");
        System.setProperty("migration.submission.enabled", "true");
        System.setProperty("migration.submission.auth.requireSSL", "true");
        System.setProperty("migration.submission.old.host", "127.0.0.1");
        System.setProperty("migration.submission.old.port", String.valueOf(oldBackend.start()));

        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();
        proxy = MigrationProxyServer.createServer(configuration)
            .overrideWith(postgresExtension.getModule(), MigrationProxyImapProbe.MODULE, SubmissionProxyProbe.MODULE);
        proxy.start();
        probe = proxy.getProbe(SubmissionProxyProbe.class);
    }

    @AfterAll
    static void tearDownAll() {
        if (proxy != null) {
            proxy.stop();
        }
        if (oldBackend != null) {
            oldBackend.close();
        }
        List.of("enabled", "auth.requireSSL", "old.host", "old.port")
            .forEach(key -> System.clearProperty("migration.submission." + key));
    }

    @BeforeEach
    void setUp() {
        oldBackend.receivedLines().clear();
    }

    @Test
    void authShouldNotBeAnnouncedBeforeStartTls() throws Exception {
        try (ProxySmtpClient client = connect()) {
            assertThat(client.command("EHLO client.tld"))
                .anyMatch(line -> line.endsWith("STARTTLS"))
                .noneMatch(line -> line.contains("AUTH"));
        }
    }

    @Test
    void authShouldBeRejectedBeforeStartTls() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("538 5.7.11 Encryption required for requested authentication mechanism");
            assertThat(client.reply("AUTH LOGIN"))
                .isEqualTo("538 5.7.11 Encryption required for requested authentication mechanism");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authShouldBeAnnouncedAndRelayedAfterStartTls() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            client.startTls();

            assertThat(client.command("EHLO client.tld")).contains("250-AUTH PLAIN LOGIN");
            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
        }
    }

    @Test
    void authShouldBeAnnouncedAndRelayedOverImplicitTls() throws Exception {
        try (ProxySmtpClient client = ProxySmtpClient.implicitTls(probe.implicitTlsPort())) {
            client.greeting();

            assertThat(client.command("EHLO client.tld")).contains("250-AUTH PLAIN LOGIN");
            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
        }
    }

    private static ProxySmtpClient connect() throws Exception {
        ProxySmtpClient client = ProxySmtpClient.plain(probe.startTlsPort());
        client.greeting();
        return client;
    }
}
