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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.protocols.sasl.kerberos.GssapiTestClient;
import org.apache.james.protocols.sasl.kerberos.KerberosTestExtension;
import org.apache.james.protocols.sasl.kerberos.KerberosTestFixture;
import org.apache.james.server.core.configuration.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.linagora.tmail.migration.imap.ImapBackendDialog;

/**
 * End to end coverage of the optional Kerberos support: a real KDC issues the client its ticket, the
 * proxy authenticates it with GSSAPI and opens the backend session by SASL PLAIN delegation - it never
 * sees a user password.
 */
// The embedded KDC temporarily replaces the JVM-wide Kerberos configuration.
@ResourceLock(KerberosTestFixture.KRB5_CONFIGURATION_RESOURCE)
class MigrationGssapiTest {
    private static final String SERVICE = "imap";
    // alice@JAMES.TEST is the principal the KDC fixture issues tickets for. The proxy maps it to a James
    // username as James itself does: no realm stripping, the realm is the domain.
    private static final String USER = "alice@james.test";
    private static final String ADMIN = "admin@james.test";
    private static final String ADMIN_PASSWORD = "admin-password";
    private static final int PROXY_IMAP_PORT = 10143; // conf/imapserver.xml binds the proxy IMAP server here
    private static final int MAX_SASL_ROUNDS = 10;

    @Order(1)
    @RegisterExtension
    static KerberosTestExtension kerberos = new KerberosTestExtension(SERVICE);

    @Order(2)
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @TempDir
    static File workingDirectory;

    private static final Map<String, String> PREVIOUS_PROPERTIES = new HashMap<>();

    private static StubBackendServer oldBackend;
    private static GuiceJamesServer proxy;

    @BeforeAll
    static void setUpAll() throws Exception {
        // The old backend authorizes the configured administrator as the user, then serves the session.
        oldBackend = new StubBackendServer("* OK backend ready")
            .reply(ImapBackendDialog.PROXY_TAG + " AUTHENTICATE PLAIN", "+ ")
            .reply(expectedDelegation(), ImapBackendDialog.PROXY_TAG + " OK AUTHENTICATE completed");
        int backendPort = oldBackend.start();

        setProperty("migration.imap.old.host", "127.0.0.1");
        setProperty("migration.imap.old.port", String.valueOf(backendPort));
        setProperty("migration.imap.old.admin.username", ADMIN);
        setProperty("migration.imap.old.admin.password", ADMIN_PASSWORD);
        setProperty("migration.imap.new.admin.username", ADMIN);
        setProperty("migration.imap.new.admin.password", ADMIN_PASSWORD);
        setProperty("migration.kerberos.enabled", "true");
        setProperty("migration.kerberos.serviceName", SERVICE);
        setProperty("migration.kerberos.serverName", KerberosTestExtension.SERVER_NAME);
        setProperty("migration.kerberos.principal", kerberos.service(SERVICE).principal());
        setProperty("migration.kerberos.keyTab", kerberos.service(SERVICE).keyTab().toString());
        // This test speaks plain text IMAP: a deployment keeps the default and serves GSSAPI over TLS only.
        setProperty("migration.kerberos.requireSSL", "false");

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
        PREVIOUS_PROPERTIES.forEach((property, previousValue) -> {
            if (previousValue == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previousValue);
            }
        });
        PREVIOUS_PROPERTIES.clear();
    }

    @Test
    void capabilityShouldDisableLoginAndAdvertiseGssapi() throws Exception {
        try (ProxyClient client = new ProxyClient()) {
            client.send("a1 CAPABILITY");

            assertThat(client.untilTagged("a1"))
                .anySatisfy(line -> assertThat(line)
                    .startsWith("* CAPABILITY")
                    .contains("LOGINDISABLED")
                    .contains("AUTH=GSSAPI")
                    .doesNotContain("AUTH=PLAIN"));
        }
    }

    @Test
    void loginShouldBeRejected() throws Exception {
        try (ProxyClient client = new ProxyClient()) {
            client.send("a1 LOGIN " + USER + " whatever");

            assertThat(client.tagged("a1")).startsWith("a1 NO");
        }
    }

    @Test
    void unsupportedMechanismShouldBeRejected() throws Exception {
        try (ProxyClient client = new ProxyClient()) {
            client.send("a1 AUTHENTICATE PLAIN");

            assertThat(client.tagged("a1")).startsWith("a1 NO");
        }
    }

    @Test
    void gssapiShouldAuthenticateThenDelegateTheBackendSessionToTheAdmin() throws Exception {
        try (ProxyClient client = new ProxyClient();
             GssapiTestClient gssapiClient = kerberos.client(SERVICE)) {
            client.send("a1 AUTHENTICATE GSSAPI " + encode(gssapiClient.initialResponse()));
            String response = client.completeGssapiExchange(gssapiClient);

            // The proxy never replays a user password: it authenticates as the admin, authorized as the user.
            assertThat(oldBackend.receivedLines())
                .contains(ImapBackendDialog.PROXY_TAG + " AUTHENTICATE PLAIN", expectedDelegation());
            assertThat(response).startsWith("a1 OK");
            assertThat(response).contains("old backend");
        }
    }

    private static String encode(byte[] token) {
        return Base64.getEncoder().encodeToString(token);
    }

    private static String expectedDelegation() {
        return encode((USER + '\0' + ADMIN + '\0' + ADMIN_PASSWORD).getBytes(StandardCharsets.UTF_8));
    }

    private static void setProperty(String property, String value) {
        PREVIOUS_PROPERTIES.put(property, System.getProperty(property));
        System.setProperty(property, value);
    }

    /**
     * A raw IMAP client: the proxy speaks a hand written subset of IMAP, so a line oriented socket is a
     * closer reading of what is on the wire than a full blown client library.
     */
    private static final class ProxyClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader reader;

        private ProxyClient() throws IOException {
            this.socket = new Socket("127.0.0.1", PROXY_IMAP_PORT);
            this.socket.setSoTimeout(60_000);
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            assertThat(readLine()).startsWith("* OK");
        }

        private void send(String line) throws IOException {
            OutputStream out = socket.getOutputStream();
            out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private String readLine() throws IOException {
            String line = reader.readLine();
            if (line == null) {
                throw new AssertionError("The proxy closed the connection");
            }
            return line;
        }

        private String tagged(String tag) throws IOException {
            return untilTagged(tag).getLast();
        }

        private List<String> untilTagged(String tag) throws IOException {
            List<String> lines = new ArrayList<>();
            while (lines.stream().noneMatch(line -> line.startsWith(tag + " "))) {
                lines.add(readLine());
            }
            return lines;
        }

        /**
         * Relays each server challenge through the JDK GSSAPI client until the proxy answers a tagged line.
         */
        private String completeGssapiExchange(GssapiTestClient gssapiClient) throws Exception {
            for (int round = 0; round < MAX_SASL_ROUNDS; round++) {
                String line = readLine();
                if (!line.startsWith("+")) {
                    return line;
                }
                send(encode(gssapiClient.evaluate(challenge(line))));
            }
            throw new AssertionError("The GSSAPI exchange did not complete in " + MAX_SASL_ROUNDS + " rounds");
        }

        private static byte[] challenge(String continuation) {
            String payload = continuation.substring(1).trim();
            return payload.isEmpty() ? new byte[0] : Base64.getDecoder().decode(payload);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
