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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.migration.imap.ImapBackendDialog;
import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;

/**
 * Without Kerberos, {@code AUTHENTICATE PLAIN} is the SASL form of {@code LOGIN}: the proxy intercepts
 * it, replays the captured credentials against the resolved backend and, on success, hands the
 * connection over to the relay.
 */
class MigrationPlainAuthenticationTest {
    private static final String DOMAIN = "managed.tld";
    private static final String USER = "bob@" + DOMAIN; // not migrated: the proxy relays it to the old backend
    private static final String PASSWORD = "secret";
    private static final String REJECTED_USER = "mallory@" + DOMAIN;
    private static final int PROXY_IMAP_PORT = 10143; // conf/imapserver.xml binds the proxy IMAP server here

    @RegisterExtension
    static PostgresExtension postgresExtension =
        // The proxy outlives the per test schema reset, so the extension has to recreate its table
        // rather than leave it dropped behind it.
        PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    @TempDir
    static File workingDirectory;

    private static StubBackendServer oldBackend;
    private static GuiceJamesServer proxy;

    @BeforeAll
    static void setUpAll() throws Exception {
        // The backend is the only authority on the password: it accepts bob and rejects mallory.
        oldBackend = new StubBackendServer("* OK backend ready")
            .reply(loginCommand(USER), ImapBackendDialog.PROXY_TAG + " OK LOGIN completed")
            .reply(loginPrefix(REJECTED_USER), ImapBackendDialog.PROXY_TAG + " NO LOGIN failed");
        int backendPort = oldBackend.start();
        System.setProperty("migration.imap.old.host", "127.0.0.1");
        System.setProperty("migration.imap.old.port", String.valueOf(backendPort));

        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();
        proxy = MigrationProxyServer.createServer(configuration).overrideWith(postgresExtension.getModule());
        proxy.start();
    }

    @AfterAll
    static void tearDownAll() {
        if (proxy != null) {
            proxy.stop();
        }
        if (oldBackend != null) {
            oldBackend.close();
        }
        System.clearProperty("migration.imap.old.host");
        System.clearProperty("migration.imap.old.port");
    }

    @Test
    void capabilityShouldAdvertisePlainAndKeepLoginEnabled() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 CAPABILITY");

            assertThat(client.untilTagged("a1"))
                .anySatisfy(line -> assertThat(line)
                    .startsWith("* CAPABILITY")
                    .contains("AUTH=PLAIN")
                    .contains("SASL-IR")
                    .doesNotContain("LOGINDISABLED")
                    .doesNotContain("AUTH=GSSAPI"));
        }
    }

    @Test
    void authenticatePlainShouldReplayTheCredentialsAgainstTheBackend() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN " + plainResponse(USER, PASSWORD));

            assertThat(client.tagged("a1")).startsWith("a1 OK");
            assertThat(oldBackend.receivedLines()).contains(loginCommand(USER));
        }
    }

    @Test
    void authenticatePlainShouldSupportAContinuationRatherThanAnInitialResponse() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN");
            assertThat(client.readLine()).startsWith("+");

            client.send(plainResponse(USER, PASSWORD));

            assertThat(client.tagged("a1")).startsWith("a1 OK");
        }
    }

    @Test
    void authenticatePlainShouldSetUpTheRelayForTheEnsuingExchange() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN " + plainResponse(USER, PASSWORD));
            assertThat(client.tagged("a1")).startsWith("a1 OK");

            // From now on the proxy is a bare byte pipe: the command reaches the backend unparsed.
            client.send("a2 SELECT INBOX");

            await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(oldBackend.receivedLines()).contains("a2 SELECT INBOX"));
        }
    }

    @Test
    void authenticatePlainShouldBeRejectedWhenTheBackendRejectsTheCredentials() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN " + plainResponse(REJECTED_USER, "wrong"));

            assertThat(client.tagged("a1")).isEqualTo("a1 NO AUTHENTICATE failed against backend.");
        }
    }

    @Test
    void authenticatePlainShouldRejectDelegation() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN " + encode(REJECTED_USER + '\0' + USER + '\0' + PASSWORD));

            assertThat(client.tagged("a1")).isEqualTo("a1 NO AUTHENTICATE failed.");
        }
    }

    @Test
    void authenticatePlainShouldRejectAMalformedInitialResponse() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN @@@@");

            assertThat(client.tagged("a1")).startsWith("a1 BAD");
        }
    }

    @Test
    void authenticatePlainShouldRejectAPayloadThatIsNotSaslPlain() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN " + encode("no-separator-here"));

            assertThat(client.tagged("a1")).isEqualTo("a1 NO AUTHENTICATE failed.");
        }
    }

    @Test
    void authenticatePlainShouldSupportClientAbort() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE PLAIN");
            assertThat(client.readLine()).startsWith("+");

            client.send("*");

            assertThat(client.tagged("a1")).isEqualTo("a1 NO AUTHENTICATE aborted.");
        }
    }

    @Test
    void authenticateGssapiShouldBeRejectedWhenKerberosIsDisabled() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(PROXY_IMAP_PORT)) {
            client.send("a1 AUTHENTICATE GSSAPI");

            assertThat(client.tagged("a1")).isEqualTo("a1 NO Unsupported authentication mechanism.");
        }
    }

    private static String loginCommand(String username) {
        return loginPrefix(username) + " \"" + PASSWORD + "\"";
    }

    /**
     * Matches whatever password the client sent, which the rejecting backend does not care about.
     */
    private static String loginPrefix(String username) {
        return ImapBackendDialog.PROXY_TAG + " LOGIN \"" + username + "\"";
    }

    private static String plainResponse(String username, String password) {
        return encode('\0' + username + '\0' + password);
    }

    private static String encode(String payload) {
        return Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }
}
