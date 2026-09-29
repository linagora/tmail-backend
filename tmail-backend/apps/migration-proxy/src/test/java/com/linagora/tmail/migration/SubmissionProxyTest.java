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

import static com.linagora.tmail.migration.ProxySmtpClient.encode;
import static com.linagora.tmail.migration.ProxySmtpClient.plainResponse;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.Username;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.base.Strings;
import com.linagora.tmail.migration.postgres.MigratedUsersDataDefinition;
import com.linagora.tmail.migration.postgres.PostgresMigratedUsersDAO;

import io.restassured.RestAssured;

/**
 * The SMTP submission proxy end to end: the proxy authenticates the client against the submission port
 * of the backend the user belongs to, then relays the connection raw. Both backends are scripted stubs
 * that record every line they receive.
 */
class SubmissionProxyTest {
    private static final String DOMAIN = "managed.tld";
    private static final String PASSWORD = "secret";
    private static final String BOB = "bob@" + DOMAIN; // not migrated: relayed to the old backend
    private static final String ALICE = "alice@" + DOMAIN; // migrated: relayed to the new backend
    private static final String MALLORY = "mallory@" + DOMAIN; // the old backend rejects her password
    private static final String CAROL = "carol@" + DOMAIN; // the old backend fails temporarily on her AUTH
    private static final String RECIPIENT = "someone@external.tld";

    @RegisterExtension
    static PostgresExtension postgresExtension =
        // The proxy outlives the per test schema reset, so the extension has to recreate its table
        // rather than leave it dropped behind it.
        PostgresExtension.withoutRowLevelSecurity(MigratedUsersDataDefinition.MODULE);

    @TempDir
    static File workingDirectory;

    private static StubBackendServer oldBackend;
    private static StubBackendServer newBackend;
    private static GuiceJamesServer proxy;
    private static SubmissionProxyProbe probe;

    @BeforeAll
    static void setUpAll() throws Exception {
        oldBackend = scriptedBackend("old")
            .reply(authCommand(BOB, PASSWORD), "235 2.7.0 Authentication successful")
            .reply(authCommand(MALLORY, "wrong"), "535 5.7.8 Authentication credentials invalid")
            .reply(authCommand(CAROL, PASSWORD), "454 4.7.0 Temporary authentication failure");
        newBackend = scriptedBackend("new")
            .reply(authCommand(ALICE, PASSWORD), "235 2.7.0 Authentication successful");
        System.setProperty("migration.submission.enabled", "true");
        System.setProperty("migration.submission.old.host", "127.0.0.1");
        System.setProperty("migration.submission.old.port", String.valueOf(oldBackend.start()));
        System.setProperty("migration.submission.new.host", "127.0.0.1");
        System.setProperty("migration.submission.new.port", String.valueOf(newBackend.start()));
        System.setProperty("migration.submission.ehlo.extensions", "SIZE 52428800, SMTPUTF8");

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
        if (newBackend != null) {
            newBackend.close();
        }
        List.of("enabled", "old.host", "old.port", "new.host", "new.port", "ehlo.extensions")
            .forEach(key -> System.clearProperty("migration.submission." + key));
    }

    @BeforeEach
    void setUp() {
        new PostgresMigratedUsersDAO(postgresExtension.getDefaultPostgresExecutor())
            .insert(Username.of(ALICE))
            .block();
        oldBackend.receivedLines().clear();
        newBackend.receivedLines().clear();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(
            proxy.getProbe(WebAdminGuiceProbe.class).getWebAdminPort()).build();
    }

    @AfterEach
    void tearDown() {
        RestAssured.reset();
    }

    @Test
    void ehloShouldAnnounceAuthStartTlsAndTheConfiguredExtensions() throws Exception {
        try (ProxySmtpClient client = connect()) {
            assertThat(client.command("EHLO client.tld"))
                .contains("250-AUTH PLAIN LOGIN", "250-AUTH=PLAIN LOGIN", "250-STARTTLS", "250-PIPELINING",
                    "250-8BITMIME", "250-ENHANCEDSTATUSCODES", "250-SIZE 52428800")
                .last().isEqualTo("250 SMTPUTF8");
        }
    }

    @Test
    void ehloShouldNotAnnounceStartTlsOnceTlsIsActive() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            client.startTls();

            assertThat(client.command("EHLO client.tld"))
                .contains("250-AUTH PLAIN LOGIN")
                .noneMatch(line -> line.contains("STARTTLS"));
        }
    }

    @Test
    void heloShouldBeAccepted() throws Exception {
        try (ProxySmtpClient client = connect()) {
            assertThat(client.reply("HELO client.tld")).startsWith("250 ");
        }
    }

    @Test
    void mailTransactionCommandsShouldRequireAuthentication() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("530 5.7.0 Authentication required");
            assertThat(client.reply("RCPT TO:<" + RECIPIENT + ">")).isEqualTo("530 5.7.0 Authentication required");
            assertThat(client.reply("DATA")).isEqualTo("530 5.7.0 Authentication required");
            assertThat(client.reply("BDAT 10 LAST")).isEqualTo("530 5.7.0 Authentication required");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void unknownCommandsShouldBeRejected() throws Exception {
        try (ProxySmtpClient client = connect()) {
            assertThat(client.reply("VRFY " + BOB)).startsWith("500 ");
        }
    }

    @Test
    void noopRsetAndQuitShouldBeAnsweredByTheProxyBeforeAuthentication() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("NOOP")).startsWith("250 ");
            assertThat(client.reply("RSET")).startsWith("250 ");
            assertThat(client.reply("QUIT")).startsWith("221 ");
            assertThat(client.isClosedByPeer(5_000)).isTrue();
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authPlainShouldReplayTheCredentialsAgainstTheOldBackend() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
        }
        assertThat(oldBackend.receivedLines())
            .containsSubsequence("EHLO submission.proxy.tld", authCommand(BOB, PASSWORD));
        assertThat(newBackend.receivedLines()).isEmpty();
    }

    @Test
    void theMailTransactionShouldBeRelayedToTheOldBackend() throws Exception {
        try (ProxySmtpClient client = authenticated(BOB)) {
            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("250 2.1.0 Sender OK on old");
            assertThat(client.reply("RCPT TO:<" + RECIPIENT + ">")).isEqualTo("250 2.1.5 Recipient OK on old");
            assertThat(client.reply("DATA")).isEqualTo("354 Start mail input on old");
            client.sendRaw("Subject: hello\r\n\r\nbody line\r\n");
            assertThat(client.reply(".")).isEqualTo("250 2.0.0 Queued on old");
        }
        assertThat(oldBackend.receivedLines())
            .containsSubsequence(authCommand(BOB, PASSWORD), "MAIL FROM:<" + BOB + ">", "RCPT TO:<" + RECIPIENT + ">",
                "DATA", "Subject: hello", "", "body line", ".");
    }

    @Test
    void aMigratedUserShouldBeRelayedToTheNewBackend() throws Exception {
        try (ProxySmtpClient client = authenticated(ALICE)) {
            assertThat(client.reply("MAIL FROM:<" + ALICE + ">")).isEqualTo("250 2.1.0 Sender OK on new");
            assertThat(client.reply("RCPT TO:<" + RECIPIENT + ">")).isEqualTo("250 2.1.5 Recipient OK on new");
            assertThat(client.reply("DATA")).isEqualTo("354 Start mail input on new");
            client.sendRaw("Subject: hello\r\n\r\nbody line\r\n");
            assertThat(client.reply(".")).isEqualTo("250 2.0.0 Queued on new");
        }
        assertThat(newBackend.receivedLines())
            .containsSubsequence("EHLO submission.proxy.tld", authCommand(ALICE, PASSWORD), "MAIL FROM:<" + ALICE + ">");
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authPlainShouldSupportAContinuationRatherThanAnInitialResponse() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            assertThat(client.reply("AUTH PLAIN")).startsWith("334");

            assertThat(client.reply(plainResponse(BOB, PASSWORD))).isEqualTo("235 2.7.0 Authentication successful");
            assertThat(client.reply("NOOP")).isEqualTo("250 2.0.0 NOOP on old");
        }
    }

    @Test
    void authLoginShouldBeReplayedAsAuthPlainAgainstTheBackend() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH LOGIN")).isEqualTo("334 " + encode("Username:"));
            assertThat(client.reply(encode(BOB))).isEqualTo("334 " + encode("Password:"));
            assertThat(client.reply(encode(PASSWORD))).isEqualTo("235 2.7.0 Authentication successful");
            assertThat(client.reply("NOOP")).isEqualTo("250 2.0.0 NOOP on old");
        }
        assertThat(oldBackend.receivedLines()).contains(authCommand(BOB, PASSWORD));
    }

    @Test
    void authLoginShouldAcceptTheUsernameAsInitialResponse() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH LOGIN " + encode(BOB))).isEqualTo("334 " + encode("Password:"));
            assertThat(client.reply(encode(PASSWORD))).isEqualTo("235 2.7.0 Authentication successful");
        }
    }

    @Test
    void mechanismNamesShouldBeCaseInsensitive() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("auth plain " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
        }
    }

    @Test
    void authShouldFailWhenTheBackendRejectsTheCredentials() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(MALLORY, "wrong")))
                .isEqualTo("535 5.7.8 Authentication credentials invalid");
        }
    }

    @Test
    void theClientShouldBeAbleToAuthenticateAgainAfterABackendRejection() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            assertThat(client.reply("AUTH PLAIN " + plainResponse(MALLORY, "wrong"))).startsWith("535 ");

            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("530 5.7.0 Authentication required");
            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("250 2.1.0 Sender OK on old");
        }
    }

    @Test
    void authShouldFailTemporarilyWhenTheBackendFailsTemporarily() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(CAROL, PASSWORD)))
                .isEqualTo("454 4.7.0 Temporary authentication failure");
        }
    }

    @Test
    void authShouldRejectDelegation() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + encode(MALLORY + '\0' + BOB + '\0' + PASSWORD)))
                .isEqualTo("535 5.7.8 Authentication credentials invalid");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authShouldRejectAMalformedInitialResponse() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN @@@@")).startsWith("501 5.5.2");
        }
    }

    @Test
    void authShouldRejectAMalformedContinuation() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            client.reply("AUTH PLAIN");

            assertThat(client.reply("@@@@")).startsWith("501 5.5.2");
            assertThat(client.reply("NOOP")).startsWith("250 ");
        }
    }

    @Test
    void authShouldRejectAPayloadThatIsNotSaslPlain() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + encode("no-separator-here"))).matches("(501|535) .*");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void theClientShouldBeAbleToAbortTheExchange() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            client.reply("AUTH LOGIN");

            assertThat(client.reply("*")).isEqualTo("501 5.0.0 Authentication aborted");
            // Back to commands: the proxy answers NOOP itself, nothing is relayed.
            assertThat(client.reply("NOOP")).startsWith("250 ").doesNotContain("on old");
        }
        assertThat(oldBackend.receivedLines()).isEmpty();
    }

    @Test
    void authShouldRejectUnsupportedMechanisms() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH CRAM-MD5")).isEqualTo("504 5.5.4 Unrecognized authentication type");
            assertThat(client.reply("AUTH XOAUTH2 dG9rZW4=")).isEqualTo("504 5.5.4 Unrecognized authentication type");
            assertThat(client.reply("AUTH GSSAPI")).isEqualTo("504 5.5.4 Unrecognized authentication type");
        }
    }

    @Test
    void authShouldRequireAMechanism() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH")).startsWith("501 5.5.2");
        }
    }

    @Test
    void commandsPipelinedBehindAuthPlainShouldBeRelayedInOrder() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            client.sendRaw("AUTH PLAIN " + plainResponse(BOB, PASSWORD) + "\r\n"
                + "MAIL FROM:<" + BOB + ">\r\n"
                + "RCPT TO:<" + RECIPIENT + ">\r\n"
                + "DATA\r\n");

            assertThat(client.readReply()).containsExactly("235 2.7.0 Authentication successful");
            assertThat(client.readReply()).containsExactly("250 2.1.0 Sender OK on old");
            assertThat(client.readReply()).containsExactly("250 2.1.5 Recipient OK on old");
            assertThat(client.readReply()).containsExactly("354 Start mail input on old");
            client.sendRaw("Subject: pipelined\r\n\r\nbody\r\n");
            assertThat(client.reply(".")).isEqualTo("250 2.0.0 Queued on old");
        }
        assertThat(oldBackend.receivedLines())
            .containsSubsequence(authCommand(BOB, PASSWORD), "MAIL FROM:<" + BOB + ">", "RCPT TO:<" + RECIPIENT + ">",
                "DATA", "Subject: pipelined", "", "body", ".");
    }

    @Test
    void aLinePartiallyReceivedAlongWithAuthShouldBeRelayedIntact() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");

            client.sendRaw("AUTH PLAIN " + plainResponse(BOB, PASSWORD) + "\r\nMAIL FR");
            assertThat(client.readReply()).containsExactly("235 2.7.0 Authentication successful");
            Thread.sleep(200);
            client.sendRaw("OM:<" + BOB + ">\r\n");

            assertThat(client.readReply()).containsExactly("250 2.1.0 Sender OK on old");
        }
        assertThat(oldBackend.receivedLines()).contains("MAIL FROM:<" + BOB + ">");
    }

    @Test
    void aLargeMessageShouldBeRelayedByteForByte() throws Exception {
        // Far beyond the line length the SMTP stack of the proxy would frame: only a raw relay carries it.
        String longLine = "X-Long: " + Strings.repeat("a", 60_000);
        List<String> bodyLines = IntStream.range(0, 20_000)
            .mapToObj(i -> String.format("%05d ", i) + Strings.repeat("b", 94))
            .toList();

        try (ProxySmtpClient client = authenticated(BOB)) {
            client.reply("MAIL FROM:<" + BOB + ">");
            client.reply("RCPT TO:<" + RECIPIENT + ">");
            client.reply("DATA");
            client.sendRaw(longLine + "\r\n\r\n" + String.join("\r\n", bodyLines) + "\r\n");

            assertThat(client.reply(".")).isEqualTo("250 2.0.0 Queued on old");
        }
        List<String> received = oldBackend.receivedLines();
        int start = received.indexOf(longLine);
        assertThat(start).isNotNegative();
        assertThat(received.subList(start + 2, start + 2 + bodyLines.size())).isEqualTo(bodyLines);
    }

    @Test
    void authenticationAfterStartTlsShouldBeRelayed() throws Exception {
        try (ProxySmtpClient client = connect()) {
            client.command("EHLO client.tld");
            client.startTls();
            client.command("EHLO client.tld");

            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("250 2.1.0 Sender OK on old");
        }
    }

    @Test
    void authenticationOverImplicitTlsShouldBeRelayed() throws Exception {
        try (ProxySmtpClient client = ProxySmtpClient.implicitTls(probe.implicitTlsPort())) {
            client.greeting();
            assertThat(client.command("EHLO client.tld"))
                .contains("250-AUTH PLAIN LOGIN")
                .noneMatch(line -> line.contains("STARTTLS"));

            assertThat(client.reply("AUTH PLAIN " + plainResponse(BOB, PASSWORD)))
                .isEqualTo("235 2.7.0 Authentication successful");
            assertThat(client.reply("MAIL FROM:<" + BOB + ">")).isEqualTo("250 2.1.0 Sender OK on old");
        }
    }

    @Test
    void startTlsShouldBeRejectedOnceRelayed() throws Exception {
        try (ProxySmtpClient client = authenticated(BOB)) {
            // Relayed as is: the backend, not the proxy, answers.
            assertThat(client.reply("STARTTLS")).isEqualTo("454 4.7.0 TLS not available on old");
        }
    }

    @Test
    void migratingTheUserShouldCutItsRelayedSession() throws Exception {
        try (ProxySmtpClient client = authenticated(BOB)) {
            given()
                .put("/migratedUsers/" + BOB)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            await().atMost(Duration.ofSeconds(30))
                .until(() -> client.isClosedByPeer(500));
        } finally {
            given().delete("/migratedUsers/" + BOB);
        }
    }

    @Test
    void closingTheClientShouldCloseTheBackendConnection() throws Exception {
        try (ProxySmtpClient client = authenticated(BOB)) {
            assertThat(oldBackend.lastConnectionOpen()).isTrue();
        }

        await().atMost(Duration.ofSeconds(30))
            .until(() -> !oldBackend.lastConnectionOpen());
    }

    @Test
    void theBackendClosingShouldCloseTheClientConnection() throws Exception {
        try (ProxySmtpClient client = authenticated(BOB)) {
            assertThat(client.reply("QUIT")).isEqualTo("221 2.0.0 Bye from old");

            await().atMost(Duration.ofSeconds(30))
                .until(() -> client.isClosedByPeer(500));
        }
    }

    private static ProxySmtpClient connect() throws Exception {
        ProxySmtpClient client = ProxySmtpClient.plain(probe.startTlsPort());
        client.greeting();
        return client;
    }

    private static ProxySmtpClient authenticated(String username) throws Exception {
        ProxySmtpClient client = connect();
        client.command("EHLO client.tld");
        assertThat(client.reply("AUTH PLAIN " + plainResponse(username, PASSWORD)))
            .isEqualTo("235 2.7.0 Authentication successful");
        return client;
    }

    private static StubBackendServer scriptedBackend(String name) {
        return new StubBackendServer("220 " + name + ".backend ESMTP")
            .reply("EHLO", "250-" + name + ".backend\r\n250-PIPELINING\r\n250 AUTH PLAIN LOGIN")
            .reply("MAIL FROM", "250 2.1.0 Sender OK on " + name)
            .reply("RCPT TO", "250 2.1.5 Recipient OK on " + name)
            .reply("DATA", "354 Start mail input on " + name)
            .reply("NOOP", "250 2.0.0 NOOP on " + name)
            .reply("STARTTLS", "454 4.7.0 TLS not available on " + name)
            .reply(".", "250 2.0.0 Queued on " + name)
            .replyThenClose("QUIT", "221 2.0.0 Bye from " + name);
    }

    private static String authCommand(String username, String password) {
        return "AUTH PLAIN " + plainResponse(username, password);
    }
}
