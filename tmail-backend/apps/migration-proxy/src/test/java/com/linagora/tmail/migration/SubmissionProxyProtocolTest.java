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
 * With {@code forwardProxyInfo}, the backend sees the address of the client rather than the one of the
 * proxy: the PROXY protocol header received from the load balancer is forwarded to it.
 */
class SubmissionProxyProtocolTest {
    private static final String BOB = "bob@managed.tld";
    private static final String PASSWORD = "secret";
    private static final String PROXY_HEADER = "PROXY TCP4 10.1.2.3 10.9.8.7 40000 587";

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
            .reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD), "235 2.7.0 Authentication successful")
            .reply("MAIL FROM", "250 2.1.0 Sender OK on old");
        System.setProperty("migration.submission.enabled", "true");
        System.setProperty("migration.submission.old.host", "127.0.0.1");
        System.setProperty("migration.submission.old.port", String.valueOf(oldBackend.start()));
        System.setProperty("migration.submission.old.forwardProxyInfo", "true");

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
        List.of("enabled", "old.host", "old.port", "old.forwardProxyInfo")
            .forEach(key -> System.clearProperty("migration.submission." + key));
    }

    @BeforeEach
    void setUp() {
        oldBackend.receivedLines().clear();
    }

    @Test
    void theClientAddressShouldBeForwardedToTheBackend() throws Exception {
        try (ProxySmtpClient client = ProxySmtpClient.plain(probe.haproxyPort())) {
            client.send(PROXY_HEADER);
            client.greeting();
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("250 2.1.0 Sender OK on old");
        }
        assertThat(oldBackend.receivedLines())
            .containsSubsequence(PROXY_HEADER, "EHLO submission.proxy.tld", "AUTH PLAIN " + plainResponse(BOB, PASSWORD))
            .first().isEqualTo(PROXY_HEADER);
    }

    @Test
    void aClientAddressToForwardShouldBeRequired() throws Exception {
        try (ProxySmtpClient client = ProxySmtpClient.plain(probe.startTlsPort())) {
            client.greeting();
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("454 4.7.0 Temporary authentication failure");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }
}
