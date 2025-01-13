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

package com.linagora.tmail.route;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static net.javacrumbs.jsonunit.core.Option.IGNORING_ARRAY_ORDER;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC;
import static org.apache.james.jmap.JMAPTestingConstants.CEDRIC_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.TEN_SECONDS;

import java.time.Duration;
import java.time.Instant;

import org.apache.http.HttpStatus;
import org.apache.james.ClockExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.probe.MailboxProbe;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.AwsS3BlobStoreExtension;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;

import io.restassured.RestAssured;

public class TMailReportsIntegrationTest {

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.RABBITMQ)
            .jmapEnabled(false)
            .searchConfiguration(SearchConfiguration.scanning())
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new ClockExtension())
        .server(DistributedServer::createServer)
        .build();

    @RegisterExtension
    SMTPMessageSender messageSender = new SMTPMessageSender(DOMAIN);

    @RegisterExtension
    TestIMAPClient testIMAPClient = new TestIMAPClient();

    private int imapPort;
    private Port smtpPort;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        MailboxProbe mailboxProbe = server.getProbe(MailboxProbeImpl.class);

        DataProbe dataProbe = server.getProbe(DataProbeImpl.class);
        dataProbe.addDomain(DOMAIN);
        dataProbe.addUser(BOB.asString(), BOB_PASSWORD);
        dataProbe.addUser(ALICE.asString(), ALICE_PASSWORD);
        dataProbe.addUser(CEDRIC.asString(), CEDRIC_PASSWORD);
        mailboxProbe.createMailbox("#private", ALICE.asString(), DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", CEDRIC.asString(), DefaultMailboxes.INBOX);
        mailboxProbe.createMailbox("#private", BOB.asString(), DefaultMailboxes.INBOX);

        RestAssured.requestSpecification = WebAdminUtils.spec(server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort());

        imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
        smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
    }

    @Test
    void reportsRouteShouldBeExposed() {
        given()
            .when()
            .get("/reports/mails?duration=1d")
        .then()
            .statusCode(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "-100", "invalid", "1KB"})
    void reportsShouldReturnBadRequestWhenInvalidDurationParameter(String invalidDuration) {
        given()
            .when()
            .get("/reports/mails?duration=" + invalidDuration)
        .then()
            .statusCode(HttpStatus.SC_BAD_REQUEST);
    }

    @ParameterizedTest
    @ValueSource(strings = {"100", "1day", "1d", "1h", "1hour", "1m"})
    void reportsShouldReturnOKWhenValidDurationParameter(String invalidDuration) {
        given()
            .when()
            .get("/reports/mails?duration=" + invalidDuration)
        .then()
            .statusCode(HttpStatus.SC_OK);
    }

    @Test
    void reportRouteShouldReturnCorrectEntryWhenSatisfyDurationCondition(GuiceJamesServer jamesServer, UpdatableTickingClock clock) throws Exception {
        Instant now = Instant.parse("2021-01-01T00:00:00Z");
        clock.setInstant(now);

        // Given bob sends an email to alice
        messageSender.connect("127.0.0.1", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessage(BOB.asString(), ALICE.asString());

        // Verify that the email is received
        Thread.sleep(500);
        assertThat(testIMAPClient.connect("127.0.1", jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(ALICE, ALICE_PASSWORD)
            .select(INBOX)
            .awaitMessageCount(Awaitility.with().atMost(TEN_SECONDS), 1)
            .readFirstMessage())
            .contains("FROM: bob@domain.tld");

        String reportsResult = given()
            .when()
            .get("/reports/mails?duration=1h")
        .then()
            .statusCode(200)
            .extract().asString();

        assertThatJson(reportsResult)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("""
                [
                    {
                        "kind": "Received",
                        "subject": "test",
                        "sender": "bob@domain.tld",
                        "recipient": "alice@domain.tld",
                        "date": "${json-unit.ignore}",
                        "size": "${json-unit.ignore}"
                    },
                    {
                        "kind": "Sent",
                        "subject": "test",
                        "sender": "bob@domain.tld",
                        "recipient": "alice@domain.tld",
                        "date": "${json-unit.ignore}",
                        "size": "${json-unit.ignore}"
                    }
                ]""");
    }

    @Test
    void reportRouteShouldNotReturnEntryWhenNotSatisfyDurationCondition(UpdatableTickingClock clock) throws Exception {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");

        // Given bob sends an email to alice (3 days ago)
        clock.setInstant(now.minus(Duration.ofDays(3)));

        messageSender.connect("127.0.0.1", smtpPort)
            .authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessageWithHeaders(BOB.asString(), ALICE.asString(), message(BOB.asString(), "subject1"));

        testIMAPClient.connect("127.0.1", imapPort)
            .login(ALICE, ALICE_PASSWORD)
            .select(INBOX)
            .awaitMessageCount(Awaitility.with().atMost(TEN_SECONDS), 1);

        // bob sends an email to cedric (today)
        clock.setInstant(now);
        messageSender.connect("127.0.0.1", smtpPort)
            .authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessageWithHeaders(BOB.asString(), CEDRIC.asString(), message(BOB.asString(), "subject2"));

        testIMAPClient.connect("127.0.1", imapPort)
            .login(CEDRIC, CEDRIC_PASSWORD)
            .select(INBOX)
            .awaitMessageCount(Awaitility.with().atMost(TEN_SECONDS), 1);

        String reportsResult = given()
            .when()
            .get("/reports/mails?duration=1d")
        .then()
            .statusCode(200)
            .extract().asString();

        assertThatJson(reportsResult)
            .when(IGNORING_ARRAY_ORDER)
            .isEqualTo("""
                [
                    {
                        "kind": "Sent",
                        "subject": "subject2",
                        "sender": "bob@domain.tld",
                        "recipient": "cedric@domain.tld",
                        "date": "${json-unit.ignore}",
                        "size": "${json-unit.ignore}"
                    },
                    {
                        "kind": "Received",
                        "subject": "subject2",
                        "sender": "bob@domain.tld",
                        "recipient": "cedric@domain.tld",
                        "date": "${json-unit.ignore}",
                        "size": "${json-unit.ignore}"
                    }
                ]""");
    }

    private String message(String from, String subject) {
        return "FROM: " + from + "\r\n" +
            "subject: " + subject + "\r\n" +
            "\r\n" +
            "content\r\n" +
            ".\r\n";
    }
}
