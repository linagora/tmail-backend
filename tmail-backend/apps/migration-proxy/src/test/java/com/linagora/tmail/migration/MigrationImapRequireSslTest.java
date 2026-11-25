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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.migration.imap.ImapBackendDialog;
import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;

/**
 * On a listener that does not allow clear-text authentication (the default: neither
 * {@code plainAuthDisallowed} nor {@code auth.requireSSL} set), the password only ever transits an
 * encrypted client connection, whether it comes in a {@code LOGIN} or an {@code AUTHENTICATE PLAIN}.
 */
class MigrationImapRequireSslTest {
    private static final String DOMAIN = "managed.tld";
    private static final String USER = "bob@" + DOMAIN;
    private static final String PASSWORD = "secret";

    @RegisterExtension
    static PostgresExtension postgresExtension =
        PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    @TempDir
    static File workingDirectory;

    private static StubBackendServer oldBackend;
    private static GuiceJamesServer proxy;
    private static int startTlsPort;
    private static int plainDisabledPort;

    @BeforeAll
    static void setUpAll() throws Exception {
        oldBackend = new StubBackendServer("* OK backend ready")
            .reply(loginCommand(), ImapBackendDialog.PROXY_TAG + " OK LOGIN completed");
        System.setProperty("migration.imap.old.host", "127.0.0.1");
        System.setProperty("migration.imap.old.port", String.valueOf(oldBackend.start()));

        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();
        proxy = MigrationProxyServer.createServer(configuration)
            .overrideWith(postgresExtension.getModule(), MigrationProxyImapProbe.MODULE);
        proxy.start();
        startTlsPort = proxy.getProbe(MigrationProxyImapProbe.class).getStartTlsImapPort();
        plainDisabledPort = proxy.getProbe(MigrationProxyImapProbe.class).getPlainDisabledImapPort();
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

    @BeforeEach
    void setUp() {
        oldBackend.receivedLines().clear();
    }

    @Test
    void capabilityShouldAnnounceLoginDisabledBeforeStartTls() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(startTlsPort)) {
            client.send("a1 CAPABILITY");

            assertThat(client.untilTagged("a1"))
                .anySatisfy(line -> assertThat(line)
                    .startsWith("* CAPABILITY")
                    .contains("STARTTLS")
                    .contains("LOGINDISABLED")
                    .doesNotContain("AUTH=PLAIN"));
        }
    }

    @Test
    void loginShouldBeRefusedBeforeStartTls() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(startTlsPort)) {
            client.send("a1 LOGIN " + USER + " " + PASSWORD);

            assertThat(client.tagged("a1")).isEqualTo("a1 NO [PRIVACYREQUIRED] LOGIN requires an encrypted transport.");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authenticatePlainShouldBeRefusedBeforeStartTls() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(startTlsPort)) {
            client.send("a1 AUTHENTICATE PLAIN " + plainResponse());

            assertThat(client.tagged("a1"))
                .isEqualTo("a1 NO [PRIVACYREQUIRED] PLAIN authentication requires an encrypted transport.");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void capabilityShouldAnnouncePlainAfterStartTls() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(startTlsPort)) {
            client.startTls("a1");
            client.send("a2 CAPABILITY");

            assertThat(client.untilTagged("a2"))
                .anySatisfy(line -> assertThat(line)
                    .startsWith("* CAPABILITY")
                    .contains("AUTH=PLAIN")
                    .doesNotContain("LOGINDISABLED"));
        }
    }

    @Test
    void loginShouldBeRelayedAfterStartTls() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(startTlsPort)) {
            client.startTls("a1");
            client.send("a2 LOGIN " + USER + " " + PASSWORD);

            assertThat(client.tagged("a2")).startsWith("a2 OK");
        }
        assertThat(oldBackend.receivedLines()).contains(loginCommand());
    }

    @Test
    void authenticatePlainShouldBeRelayedAfterStartTls() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(startTlsPort)) {
            client.startTls("a1");
            client.send("a2 AUTHENTICATE PLAIN " + plainResponse());

            assertThat(client.tagged("a2")).startsWith("a2 OK");
        }
        assertThat(oldBackend.receivedLines()).contains(loginCommand());
    }

    @Test
    void loginShouldBeRefusedWithoutPrivacyRequiredWhenPlainIsDisabled() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(plainDisabledPort)) {
            client.startTls("a1");
            client.send("a2 LOGIN " + USER + " " + PASSWORD);

            assertThat(client.tagged("a2")).isEqualTo("a2 NO LOGIN is disabled.");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authenticatePlainShouldBeRefusedWithoutPrivacyRequiredWhenPlainIsDisabled() throws Exception {
        try (ProxyImapClient client = new ProxyImapClient(plainDisabledPort)) {
            client.startTls("a1");
            client.send("a2 AUTHENTICATE PLAIN " + plainResponse());

            assertThat(client.tagged("a2")).isEqualTo("a2 NO PLAIN authentication is disabled.");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    private static String loginCommand() {
        return ImapBackendDialog.PROXY_TAG + " LOGIN \"" + USER + "\" \"" + PASSWORD + "\"";
    }

    private static String plainResponse() {
        return Base64.getEncoder().encodeToString(('\0' + USER + '\0' + PASSWORD).getBytes(StandardCharsets.UTF_8));
    }
}
