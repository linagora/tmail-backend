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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.linagora.tmail.migration.imap.ImapBackendDialog;

import io.restassured.RestAssured;

/**
 * Integration test on the (memory event bus) migration proxy asserting the disconnection plumbing:
 * flagging a user as migrated over webadmin must cut its already established, proxied IMAP connection so
 * the client reconnects and lands on the new backend rather than lingering on the old one.
 *
 * <p>The proxy is booted for real (webadmin + IMAP server + in-VM {@code TMAIL_EVENT_BUS} + connection
 * registry) with a lightweight {@link StubBackendServer} standing in for the old backend, so the whole
 * webadmin &rarr; event bus &rarr; registry &rarr; channel close path is exercised end to end.
 */
class MigrationDisconnectOnMigrationTest {
    private static final String DOMAIN = "managed.tld";
    private static final String USER = "bob@" + DOMAIN; // not migrated: the proxy relays it to the old backend
    private static final String PASSWORD = "secret";
    private static final int PROXY_IMAP_PORT = 10143; // conf/imapserver.xml binds the proxy IMAP server here

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    @TempDir
    static File workingDirectory;

    private StubBackendServer oldBackend;
    private GuiceJamesServer proxy;

    @BeforeEach
    void setUp() throws Exception {
        // Old backend accepting the proxy's LOGIN so the proxied connection gets established (and tracked).
        oldBackend = new StubBackendServer("* OK backend ready")
            .reply(ImapBackendDialog.PROXY_TAG + " LOGIN", ImapBackendDialog.PROXY_TAG + " OK LOGIN completed");
        int backendPort = oldBackend.start();
        System.setProperty("migration.imap.old.host", "127.0.0.1");
        System.setProperty("migration.imap.old.port", String.valueOf(backendPort));

        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();
        proxy = MigrationProxyServer.createServer(configuration).overrideWith(postgresExtension.getModule());
        proxy.start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(
            proxy.getProbe(WebAdminGuiceProbe.class).getWebAdminPort()).build();
    }

    @AfterEach
    void tearDown() {
        if (proxy != null) {
            proxy.stop();
        }
        if (oldBackend != null) {
            oldBackend.close();
        }
        System.clearProperty("migration.imap.old.host");
        System.clearProperty("migration.imap.old.port");
        RestAssured.reset();
    }

    @Test
    void puttingAUserAsMigratedShouldCutItsEstablishedProxiedConnection() throws Exception {
        try (Socket clientSocket = new Socket("127.0.0.1", PROXY_IMAP_PORT)) {
            clientSocket.setSoTimeout(60_000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = clientSocket.getOutputStream();

            // GIVEN an established, authenticated and proxied IMAP connection
            out.write(("a1 LOGIN " + USER + " " + PASSWORD + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
            assertThat(readTaggedResponse(reader, "a1")).startsWith("a1 OK");

            // WHEN the user is flagged as migrated
            given()
                .put("/migratedUsers/" + USER)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            // THEN the established connection is cut (the client stream reaches EOF / is reset)
            clientSocket.setSoTimeout(500);
            await().atMost(Duration.ofSeconds(30))
                .until(() -> !stillOpen(reader));
        }
    }

    private static String readTaggedResponse(BufferedReader reader, String tag) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith(tag + " ")) {
                return line;
            }
        }
        throw new AssertionError("Backend closed the connection before answering the LOGIN");
    }

    private static boolean stillOpen(BufferedReader reader) {
        try {
            // null means the peer closed the connection (EOF); any read line means it is still open.
            return reader.readLine() != null;
        } catch (SocketTimeoutException e) {
            return true; // idle but still open: keep polling
        } catch (IOException e) {
            return false; // connection reset: the proxy closed it
        }
    }
}
