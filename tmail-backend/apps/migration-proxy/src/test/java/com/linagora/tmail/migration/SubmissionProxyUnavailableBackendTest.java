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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.base.Stopwatch;
import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;
import com.linagora.tmail.migration.postgres.PostgresMigratedUsersDAO;

/**
 * A backend the submission proxy cannot use is a temporary failure for the client ({@code 454}), never a
 * rejection of its password, and leaves the client session usable.
 */
class SubmissionProxyUnavailableBackendTest {
    private static final String BOB = "bob@managed.tld"; // not migrated: the old backend refuses connections
    private static final String ALICE = "alice@managed.tld"; // migrated: the new backend never greets
    private static final Duration HANDSHAKE_TIMEOUT = Duration.ofSeconds(2);

    @RegisterExtension
    static PostgresExtension postgresExtension =
        PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    @TempDir
    static File workingDirectory;

    private static ServerSocket silentBackend;
    private static GuiceJamesServer proxy;
    private static SubmissionProxyProbe probe;

    @BeforeAll
    static void setUpAll() throws Exception {
        int refusingPort;
        try (ServerSocket closed = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            refusingPort = closed.getLocalPort();
        }
        // Accepts TCP connections (backlog) but never sends a greeting.
        silentBackend = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());

        System.setProperty("migration.submission.enabled", "true");
        System.setProperty("migration.submission.old.host", "127.0.0.1");
        System.setProperty("migration.submission.old.port", String.valueOf(refusingPort));
        System.setProperty("migration.submission.new.host", "127.0.0.1");
        System.setProperty("migration.submission.new.port", String.valueOf(silentBackend.getLocalPort()));
        System.setProperty("migration.submission.handshakeTimeout", HANDSHAKE_TIMEOUT.toSeconds() + "s");

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
    static void tearDownAll() throws Exception {
        if (proxy != null) {
            proxy.stop();
        }
        if (silentBackend != null) {
            silentBackend.close();
        }
        List.of("enabled", "old.host", "old.port", "new.host", "new.port", "handshakeTimeout")
            .forEach(key -> System.clearProperty("migration.submission." + key));
    }

    @BeforeEach
    void setUp() {
        new PostgresMigratedUsersDAO(postgresExtension.getDefaultPostgresExecutor())
            .insert(Username.of(ALICE))
            .block();
    }

    @Test
    void aBackendRefusingConnectionsShouldBeATemporaryFailure() throws Exception {
        try (ProxySmtpClient client = connect()) {
            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, "secret")))
                .isEqualTo("454 4.7.0 Temporary authentication failure");
        }
    }

    @Test
    void aBackendNotAnsweringShouldBeATemporaryFailureAfterTheHandshakeTimeout() throws Exception {
        try (ProxySmtpClient client = connect()) {
            Stopwatch stopwatch = Stopwatch.createStarted();

            assertThat(client.reply("AUTH PLAIN " + plainResponse(ALICE, "secret")))
                .isEqualTo("454 4.7.0 Temporary authentication failure");
            assertThat(stopwatch.elapsed()).isGreaterThanOrEqualTo(HANDSHAKE_TIMEOUT);
        }
    }

    @Test
    void theClientSessionShouldRemainUsableAfterABackendFailure() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.reply("AUTH PLAIN " + plainResponse(BOB, "secret"));

            assertThat(client.reply("NOOP")).startsWith("250 ");
            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("530 5.7.0 Authentication required");
            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, "secret")))
                .isEqualTo("454 4.7.0 Temporary authentication failure");
        }
    }

    private static ProxySmtpClient connect() throws Exception {
        ProxySmtpClient client = ProxySmtpClient.plain(probe.startTlsPort());
        client.greeting();
        client.command("EHLO client.tld");
        return client;
    }
}
