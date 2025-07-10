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
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.RestAssured.with;
import static io.restassured.http.ContentType.JSON;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ACCEPT_RFC8621_VERSION_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ANDRE;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ANDRE_PASSWORD;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;
import static org.awaitility.Durations.ONE_MILLISECOND;
import static org.awaitility.Durations.ONE_MINUTE;

import java.time.Duration;
import java.util.List;

import org.apache.http.HttpStatus;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.JmapJamesServerContract;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.TestIMAPClient;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.encrypted.EncryptedMailboxManager;
import com.linagora.tmail.encrypted.InMemoryEncryptedEmailContentStoreModule;
import com.linagora.tmail.encrypted.KeystoreMemoryModule;
import com.linagora.tmail.encrypted.MailboxConfiguration;
import com.linagora.tmail.encrypted.MailboxManagerClassProbe;
import com.linagora.tmail.james.jmap.method.EncryptedEmailDetailedViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.EncryptedEmailFastViewGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreGetMethodModule;
import com.linagora.tmail.james.jmap.method.KeystoreSetMethodModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxProbe;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.restassured.specification.RequestSpecification;

class EncryptedMemoryServerTest implements JamesServerConcreteContract, JmapJamesServerContract {
    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
        MemoryConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .mailbox(new MailboxConfiguration(true))
            .usersRepository(DEFAULT)
            .build())
        .server(configuration -> MemoryServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerClassProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(TeamMailboxProbe.class))
            .overrideWith(new KeystoreMemoryModule(), new KeystoreSetMethodModule(), new KeystoreGetMethodModule(), new EncryptedEmailDetailedViewGetMethodModule(), new EncryptedEmailFastViewGetMethodModule(), new InMemoryEncryptedEmailContentStoreModule()))
        .build();

    @RegisterExtension
    public TestIMAPClient testIMAPClient = new TestIMAPClient();

    private static final Duration slowPacedPollInterval = ONE_HUNDRED_MILLISECONDS;
    private static final ConditionFactory awaitAtMostOneMinute = Awaitility.with()
        .pollInterval(slowPacedPollInterval)
        .and()
        .with()
        .pollDelay(ONE_MILLISECOND)
        .await()
        .atMost(ONE_MINUTE);

    @Disabled("POP3 server is disabled")
    @Test
    public void connectPOP3ServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        // POP3 server is disabled
    }

    @Disabled("LMTP server is disabled")
    @Test
    public void connectLMTPServerShouldSendShabangOnConnect(GuiceJamesServer jamesServer) {
        // LMTP server is disabled
    }

    @Test
    public void shouldUseEncryptedMailboxManager(GuiceJamesServer jamesServer) {
        assertThat(jamesServer.getProbe(MailboxManagerClassProbe.class).getMailboxManagerClass())
            .isEqualTo(EncryptedMailboxManager.class);
    }

    @Test
    public void shouldEncryptContent(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD);

        requestSpecification = baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(BOB, BOB_PASSWORD)))
            .build();

        String request = "{\n" +
            "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:pgp\"]," +
            "  \"methodCalls\": [" +
            "    [\"Keystore/set\", {" +
            "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
            "      \"create\": {" +
            "        \"K87\": {" +
            "          \"key\": \"-----BEGIN PGP PUBLIC KEY BLOCK-----\\n\\n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\\nx6e6cS5SeRbzaDlNKIjjEw==\\n=Q4mr\\n-----END PGP PUBLIC KEY BLOCK-----\\n\"" +
            "        }" +
            "      }" +
            "    }, \"c1\"]" +
            "  ]" +
            "}";

        with()
            .header(HttpHeaderNames.ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .body(request)
            .when()
            .post();

        SMTPMessageSender smtpMessageSender = new SMTPMessageSender("domain.com");
        smtpMessageSender
            .connect("127.0.0.1", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessageWithHeaders(BOB.asString(), BOB.asString(), "subject: test\r\n\r\nencrypt-me\r\n.\r\n");
        smtpMessageSender.close();

        testIMAPClient.connect("127.0.0.1", jamesServer.getProbe(ImapGuiceProbe.class).getImapPort())
            .login(BOB.asString(), BOB_PASSWORD)
            .select(TestIMAPClient.INBOX)
            .awaitMessage(awaitAtMostOneMinute);

        assertThat(testIMAPClient.readFirstMessage()).contains("-----BEGIN PGP MESSAGE-----");
        assertThat(testIMAPClient.readFirstMessage()).doesNotContain("encrypt-me");
    }

    @Test
    public void teamMailboxShouldNotEncryptedWhenReceiveEmailFromAuthenticateUser(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD)
            .addUser(ALICE.asString(), ALICE_PASSWORD);

        TeamMailbox teamMailbox = TeamMailbox.apply(Domain.of(DOMAIN), TeamMailboxName.fromString("marketing").toOption().get());
        jamesServer.getProbe(TeamMailboxProbe.class)
            .create(teamMailbox)
            .addMember(teamMailbox, BOB);

        // bob upload public key
        uploadPublicKey("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6", buildJmapRequestSpecification(BOB, BOB_PASSWORD, jamesServer));

        // alice send email to team-mailbox
        SMTPMessageSender smtpMessageSender = new SMTPMessageSender("domain.com");
        smtpMessageSender
            .connect("127.0.0.1", jamesServer.getProbe(SmtpGuiceProbe.class).getSmtpPort())
            .authenticate(ALICE.asString(), ALICE_PASSWORD)
            .sendMessageWithHeaders(ALICE.asString(), teamMailbox.asMailAddress().asString(), "subject: test\r\n\r\nencrypt-me-123\r\n.\r\n");
        smtpMessageSender.close();

        awaitAtMostOneMinute.untilAsserted(() -> {
            List<String> emailIds = given(buildJmapRequestSpecification(BOB, BOB_PASSWORD, jamesServer))
                .body("{" +
                    "  \"using\": [" +
                    "    \"urn:ietf:params:jmap:core\"," +
                    "    \"urn:ietf:params:jmap:mail\"," +
                    "    \"urn:apache:james:params:jmap:mail:shares\"]," +
                    "  \"methodCalls\": [[\"Email/query\", {" +
                    "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
                    "      \"filter\": {}" +
                    "    }, \"c1\"]]" +
                    "}")
                .post()
            .then()
                .statusCode(SC_OK)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .get("methodResponses[0][1].ids");

            assertThat(emailIds)
                .hasSize(1);

            given(buildJmapRequestSpecification(BOB, BOB_PASSWORD, jamesServer))
                .body(String.format("{" +
                    "  \"using\": [" +
                    "    \"urn:ietf:params:jmap:core\"," +
                    "    \"urn:ietf:params:jmap:mail\"," +
                    "    \"urn:apache:james:params:jmap:mail:shares\"]," +
                    "  \"methodCalls\": [[\"Email/get\", {" +
                    "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
                    "      \"ids\": [\"%s\"]," +
                    "      \"fetchAllBodyValues\": true" +
                    "    }, \"c1\"]]" +
                    "}", emailIds.get(0)))
            .when()
                .post()
            .then()
                .statusCode(SC_OK)
                .contentType(JSON)
                .body("methodResponses[0][1].list[0].bodyValues.(\"1\").value", Matchers.containsString("encrypt-me-123"));
        });
    }

    @Test
    public void teamMailboxShouldNotEncryptedWhenMemberAddNewMessage(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD)
            .addUser(ANDRE().asString(), ANDRE_PASSWORD());

        TeamMailbox teamMailbox = TeamMailbox.apply(Domain.of(DOMAIN), TeamMailboxName.fromString("marketing").toOption().get());
        jamesServer.getProbe(TeamMailboxProbe.class)
            .create(teamMailbox)
            .addMember(teamMailbox, BOB)
            .addMember(teamMailbox, ANDRE());

        MailboxPath teamMailboxPath = teamMailbox.mailboxPath();
        String teamMailboxId1 = jamesServer.getProbe(MailboxProbeImpl.class)
            .getMailboxId(teamMailboxPath.getNamespace(), teamMailboxPath.getUser().asString(), teamMailboxPath.getName())
            .serialize();

        // bob upload encryption key
        uploadPublicKey("29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6", buildJmapRequestSpecification(BOB, BOB_PASSWORD, jamesServer));

        // bob add a message to team mailbox
        given(buildJmapRequestSpecification(BOB, BOB_PASSWORD, jamesServer))
            .body(String.format("{" +
                "    \"using\": [" +
                "      \"urn:ietf:params:jmap:core\"," +
                "      \"urn:ietf:params:jmap:mail\"," +
                "      \"urn:apache:james:params:jmap:mail:shares\"]," +
                "    \"methodCalls\": [[\"Email/set\", {" +
                "        \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
                "        \"create\": {" +
                "          \"K39\": {" +
                "            \"mailboxIds\": {\"%s\":true}," +
                "            \"subject\": \"World domination\"," +
                "                \"htmlBody\": [" +
                "                  {" +
                "                    \"partId\": \"a49d\"," +
                "                    \"type\": \"text/html\"" +
                "                  }" +
                "                ]," +
                "                \"bodyValues\": {" +
                "                  \"a49d\": {" +
                "                    \"value\": \"Let me tell you all about it. What we do is\"," +
                "                    \"isTruncated\": false," +
                "                    \"isEncodingProblem\": false" +
                "                  }" +
                "                }" +
                "          }" +
                "        }" +
                "      }, \"c1\"]]" +
                "  }", teamMailboxId1))
        .when()
            .post()
        .then()
            .statusCode(SC_OK);

        // andre query email from team mailbox
        awaitAtMostOneMinute.untilAsserted(() -> {
            List<String> emailIds = given(buildJmapRequestSpecification(ANDRE(), ANDRE_PASSWORD(), jamesServer))
                .body("{" +
                    "  \"using\": [" +
                    "    \"urn:ietf:params:jmap:core\"," +
                    "    \"urn:ietf:params:jmap:mail\"," +
                    "    \"urn:apache:james:params:jmap:mail:shares\"]," +
                    "  \"methodCalls\": [[\"Email/query\", {" +
                    "      \"accountId\": \"1e8584548eca20f26faf6becc1704a0f352839f12c208a47fbd486d60f491f7c\"," +
                    "      \"filter\": {}" +
                    "    }, \"c1\"]]" +
                    "}")
                .post()
            .then()
                .statusCode(SC_OK)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .get("methodResponses[0][1].ids");

            assertThat(emailIds)
                .hasSize(1);

            given(buildJmapRequestSpecification(ANDRE(), ANDRE_PASSWORD(), jamesServer))
                .body(String.format("{" +
                    "  \"using\": [" +
                    "    \"urn:ietf:params:jmap:core\"," +
                    "    \"urn:ietf:params:jmap:mail\"," +
                    "    \"urn:apache:james:params:jmap:mail:shares\"]," +
                    "  \"methodCalls\": [[\"Email/get\", {" +
                    "      \"accountId\": \"1e8584548eca20f26faf6becc1704a0f352839f12c208a47fbd486d60f491f7c\"," +
                    "      \"ids\": [\"%s\"]," +
                    "      \"fetchAllBodyValues\": true" +
                    "    }, \"c1\"]]" +
                    "}", emailIds.get(0)))
            .when()
                .post()
            .then()
                .statusCode(SC_OK)
                .contentType(JSON)
                .body("methodResponses[0][1].list[0].bodyValues.(\"2\").value", Matchers.containsString("Let me tell you all about it. What we do is"));

        });
    }

    private RequestSpecification buildJmapRequestSpecification(Username username, String password, GuiceJamesServer jamesServer) {
        return baseRequestSpecBuilder(jamesServer)
            .setAuth(authScheme(new UserCredential(username, password)))
            .addHeader(HttpHeaderNames.ACCEPT.toString(), ACCEPT_RFC8621_VERSION_HEADER())
            .build();
    }

    private void uploadPublicKey(String accountId, RequestSpecification requestSpecification) {
        given(requestSpecification)
            .body(String.format("{" +
                "  \"using\": [\"urn:ietf:params:jmap:core\", \"com:linagora:params:jmap:pgp\"]," +
                "  \"methodCalls\": [" +
                "    [\"Keystore/set\", {" +
                "      \"accountId\": \"%s\"," +
                "      \"create\": {" +
                "        \"K87\": {" +
                "          \"key\": \"-----BEGIN PGP PUBLIC KEY BLOCK-----\\n\\n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\\nx6e6cS5SeRbzaDlNKIjjEw==\\n=Q4mr\\n-----END PGP PUBLIC KEY BLOCK-----\\n\"" +
                "        }" +
                "      }" +
                "    }, \"c1\"]" +
                "  ]" +
                "}", accountId))
        .when()
            .post()
        .then()
            .statusCode(HttpStatus.SC_OK);
    }

}