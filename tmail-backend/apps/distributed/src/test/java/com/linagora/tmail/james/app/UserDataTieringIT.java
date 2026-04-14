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

package com.linagora.tmail.james.app;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.SearchConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapGuiceProbe;
import org.apache.james.jmap.JmapRFCCommonRequests;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.datastax.oss.driver.api.core.CqlSession;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

class UserDataTieringIT {
    static class CassandraTieringProbe implements GuiceProbe {
        private final CqlSession session;

        @Inject
        CassandraTieringProbe(CqlSession session) {
            this.session = session;
        }

        long countEmailChanges() {
            return session.execute("SELECT COUNT(*) FROM email_change")
                .one().getLong(0);
        }

        long countMailboxChanges() {
            return session.execute("SELECT COUNT(*) FROM mailbox_change")
                .one().getLong(0);
        }

        long countThread2() {
            return session.execute("SELECT COUNT(*) FROM thread_2 ")
                .one().getLong(0);
        }

        long countFastViewEntries() {
            return session.execute("SELECT COUNT(*) FROM message_fast_view_projection")
                .one().getLong(0);
        }

        long countAttachmentV2() {
            return session.execute("SELECT COUNT(*) FROM attachmentV2")
                .one().getLong(0);
        }
    }

    private static final String DOMAIN = "tmail.org";
    private static final String BOB = "bob@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final Date OLD_INTERNAL_DATE = Date.from(Instant.now().minus(60, ChronoUnit.DAYS));
    private static final String MULTIPART_WITH_ATTACHMENT = "From: sender@tmail.org\r\n"
        + "To: bob@tmail.org\r\n"
        + "Subject: Test tiering\r\n"
        + "MIME-Version: 1.0\r\n"
        + "Content-Type: multipart/mixed; boundary=\"boundary\"\r\n"
        + "\r\n"
        + "--boundary\r\n"
        + "Content-Type: text/plain\r\n"
        + "\r\n"
        + "Body content\r\n"
        + "--boundary\r\n"
        + "Content-Type: text/plain; name=\"attachment.txt\"\r\n"
        + "Content-Disposition: attachment; filename=\"attachment.txt\"\r\n"
        + "\r\n"
        + "Attachment content\r\n"
        + "--boundary--\r\n";
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .jmapEnabled(true)
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .enableCache()
                .deduplication()
                .noCryptoConfig()
                .enableSingleSave())
            .searchConfiguration(SearchConfiguration.openSearch())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .build())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(CassandraTieringProbe.class)))
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .lifeCycle(JamesServerExtension.Lifecycle.PER_CLASS)
        .build();

    private MessageId messageId;
    private JmapRFCCommonRequests.UserCredential bobCredential;
    private RequestSpecification webadminSpec;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB, PASSWORD);

        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        mailboxProbe.createMailbox(MailboxPath.forUser(Username.of(BOB), DefaultMailboxes.INBOX));

        InputStream multipartEml = new ByteArrayInputStream(MULTIPART_WITH_ATTACHMENT.getBytes(StandardCharsets.US_ASCII));
        messageId = mailboxProbe.appendMessage(BOB, MailboxPath.inbox(Username.of(BOB)),
            multipartEml, OLD_INTERNAL_DATE, false, new Flags()).getMessageId();

        WebAdminGuiceProbe webAdminProbe = server.getProbe(WebAdminGuiceProbe.class);
        webadminSpec = WebAdminUtils.buildRequestSpecification(webAdminProbe.getWebAdminPort()).build();

        int jmapPort = server.getProbe(JmapGuiceProbe.class).getJmapPort().getValue();
        RestAssured.requestSpecification = new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setPort(jmapPort)
            .addHeader("accept", "application/json; jmapVersion=rfc-8621")
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        bobCredential = JmapRFCCommonRequests.getUserCredential(BOB, PASSWORD);

        CassandraTieringProbe probe = server.getProbe(CassandraTieringProbe.class);
        CALMLY_AWAIT.until(() -> probe.countEmailChanges() > 0);
        CALMLY_AWAIT.until(() -> probe.countMailboxChanges() > 0);
        CALMLY_AWAIT.until(() -> probe.countFastViewEntries() > 0);
    }

    @Test
    void tieringShouldClearProjectionsWhileMessageRemainsReadable(GuiceJamesServer server) {
        // Apply tiering via webadmin
        given()
            .spec(webadminSpec)
            .queryParam("tiering", "30d")
        .when()
            .post("/users/" + BOB + "/data")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        // Message can still be retrieved via JMAP Email/get
        String attachmentBlobId = given()
            .auth().basic(BOB, PASSWORD)
            .header("accept", "application/json; jmapVersion=rfc-8621")
            .body("""
                {
                  "using": ["urn:ietf:params:jmap:core", "urn:ietf:params:jmap:mail"],
                  "methodCalls": [[
                    "Email/get",
                    {
                      "accountId": "%s",
                      "ids": ["%s"],
                      "properties": ["id", "subject", "attachments"]
                    },
                    "c1"
                  ]]
                }""".formatted(bobCredential.accountId(), messageId.serialize()))
        .when()
            .post("/jmap")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("methodResponses[0][1].list", org.hamcrest.Matchers.hasSize(1))
            .extract()
            .jsonPath()
            .getString("methodResponses[0][1].list[0].attachments[0].blobId");

        // Attachment can still be downloaded using the blobId from Email/get
        given()
            .auth().basic(BOB, PASSWORD)
            .header("accept", "application/json; jmapVersion=rfc-8621")
        .when()
            .get("/download/" + bobCredential.accountId() + "/" + attachmentBlobId)
        .then()
            .statusCode(HttpStatus.OK_200);

        // All tiered tables are empty
        CassandraTieringProbe probe = server.getProbe(CassandraTieringProbe.class);
        assertThat(probe.countEmailChanges()).isZero();
        assertThat(probe.countMailboxChanges()).isZero();
        assertThat(probe.countThread2()).isZero();
        assertThat(probe.countFastViewEntries()).isZero();
        assertThat(probe.countAttachmentV2()).isZero();
    }
}
