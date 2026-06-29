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
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import com.linagora.tmail.migration.postgres.PostgresMigratedUsersDAO;

import io.restassured.RestAssured;

/**
 * End-to-end mirroring test with two real Twake Mail backends adapted to relay their users'
 * authenticated submissions to the migration proxy and to deliver locally only the mail coming back
 * from it (backend/mailetcontainer.xml). With userA hosted on old and userB hosted on new:
 *   - A writes to B  =&gt; B receives it on the NEW server,
 *   - B writes to A  =&gt; A receives it on the OLD server.
 *
 * <p>Heavy end-to-end test: it boots two real {@code linagora/tmail-backend-memory} backends (each
 * derived to bake the adapted mailetcontainer in) plus the in-JVM proxy, so it takes several minutes.
 */
class MigrationMirroringTest {
    private static final String IMAGE = "linagora/tmail-backend-memory:latest";
    private static final String DOMAIN = "managed.tld";
    private static final String USER_A = "usera@" + DOMAIN; // hosted on old (not migrated)
    private static final String USER_B = "userb@" + DOMAIN; // hosted on new (migrated)
    private static final String PASSWORD = "secret";
    private static final int PROXY_RELAY_PORT = 10026;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();
    @RegisterExtension
    TestIMAPClient imapClient = new TestIMAPClient();
    @RegisterExtension
    SMTPMessageSender messageSender = new SMTPMessageSender(DOMAIN);

    @TempDir
    static File workingDirectory;

    // Derive the backend image baking our adapted mailetcontainer.xml in (no volume mount, CI/DinD safe).
    private static final ImageFromDockerfile BACKEND_IMAGE = new ImageFromDockerfile()
        .withDockerfileFromBuilder(builder -> builder
            .from(IMAGE)
            .copy("mailetcontainer.xml", "/root/conf/mailetcontainer.xml")
            .copy("smtpserver.xml", "/root/conf/smtpserver.xml")
            .copy("imapserver.xml", "/root/conf/imapserver.xml")
            .copy("jmap.properties", "/root/conf/jmap.properties")
            .build())
        .withFileFromClasspath("mailetcontainer.xml", "backend/mailetcontainer.xml")
        .withFileFromClasspath("smtpserver.xml", "backend/smtpserver.xml")
        .withFileFromClasspath("imapserver.xml", "backend/imapserver.xml")
        .withFileFromClasspath("jmap.properties", "backend/jmap.properties");

    @SuppressWarnings("resource")
    static GenericContainer<?> oldBackend = adaptedBackend();
    @SuppressWarnings("resource")
    static GenericContainer<?> newBackend = adaptedBackend();

    private static GenericContainer<?> adaptedBackend() {
        return new GenericContainer<>(BACKEND_IMAGE)
            .withExposedPorts(25, 587, 143, 8000)
            .withEnv("MIGRATION_PROXY_HOST", "host.testcontainers.internal")
            .withEnv("MIGRATION_PROXY_PORT", String.valueOf(PROXY_RELAY_PORT))
            .waitingFor(Wait.forLogMessage(".*JAMES server started.*", 1)
                .withStartupTimeout(Duration.ofMinutes(5)));
    }

    static GuiceJamesServer proxy;

    @BeforeAll
    static void setUpAll() throws Exception {
        Testcontainers.exposeHostPorts(PROXY_RELAY_PORT);
        oldBackend.start();
        newBackend.start();

        // Proxy relays migrated recipients to new, the rest (local) to old.
        System.setProperty("migration.smtp.old.host", "127.0.0.1");
        System.setProperty("migration.smtp.old.port", String.valueOf(oldBackend.getMappedPort(25)));
        System.setProperty("migration.smtp.new.host", "127.0.0.1");
        System.setProperty("migration.smtp.new.port", String.valueOf(newBackend.getMappedPort(25)));

        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();
        proxy = MigrationProxyServer.createServer(configuration).overrideWith(postgresExtension.getModule());
        proxy.start();
        proxy.getProbe(DataProbeImpl.class).fluent().addDomain(DOMAIN);
        new PostgresMigratedUsersDAO(postgresExtension.getDefaultPostgresExecutor())
            .insert(Username.of(USER_B))
            .block();

        provisionBackend(oldBackend, USER_A);
        provisionBackend(newBackend, USER_B);
    }

    @AfterAll
    static void tearDownAll() {
        if (proxy != null) {
            proxy.stop();
        }
        oldBackend.stop();
        newBackend.stop();
    }

    @Test
    void mailIsMirroredToTheServerHostingTheRecipient() throws Exception {
        // A (on old) writes to B -> B must receive it on the new server.
        messageSender.connect("127.0.0.1", Port.of(oldBackend.getMappedPort(587)))
            .authenticate(USER_A, PASSWORD)
            .sendMessage(USER_A, USER_B);
        awaitInbox(newBackend, USER_B);

        // B (on new) writes to A -> A must receive it on the old server.
        messageSender.connect("127.0.0.1", Port.of(newBackend.getMappedPort(587)))
            .authenticate(USER_B, PASSWORD)
            .sendMessage(USER_B, USER_A);
        awaitInbox(oldBackend, USER_A);
    }

    private void awaitInbox(GenericContainer<?> backend, String user) throws Exception {
        imapClient.connect("127.0.0.1", backend.getMappedPort(143))
            .login(user, PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessageCount(await().atMost(Duration.ofSeconds(60)), 1);
    }

    private static void provisionBackend(GenericContainer<?> backend, String user) {
        RestAssured.baseURI = "http://127.0.0.1";
        Integer webAdminPort = backend.getMappedPort(8000);
        given().port(webAdminPort).put("/domains/" + DOMAIN).then().statusCode(204);
        given().port(webAdminPort).body("{\"password\":\"" + PASSWORD + "\"}")
            .put("/users/" + user).then().statusCode(204);
    }
}
