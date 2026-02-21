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

package com.linagora.tmail.james.common;

import static io.netty.handler.codec.http.HttpHeaderNames.ACCEPT;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.utils.TestIMAPClient.INBOX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.apache.james.modules.protocols.ImapGuiceProbe;
import org.apache.james.modules.protocols.SmtpGuiceProbe;
import org.apache.james.util.Port;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.SMTPMessageSender;
import org.apache.james.utils.SpoolerProbe;
import org.apache.james.utils.TestIMAPClient;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxProbe;

import io.restassured.RestAssured;
import io.restassured.authentication.PreemptiveBasicAuthScheme;

public abstract class TeamMailboxSmtpContract {
    public static final Domain DOMAIN = Domain.of("domain.tld");
    public static final TeamMailbox MARKETING_TEAM_MAILBOX = TeamMailbox.apply(DOMAIN, TeamMailboxName.fromString("marketing").toOption().get());
    public static final Username BOB = Username.fromLocalPartWithDomain("bob", DOMAIN);
    public static final String BOB_PASSWORD = "123456";
    public static final ConditionFactory AWAIT_TEN_SECONDS = Awaitility.await().atMost(Duration.ofSeconds(300));

    private SMTPMessageSender messageSender;
    private TestIMAPClient imapClient;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        Port smtpPort = server.getProbe(SmtpGuiceProbe.class).getSmtpPort();
        int imapPort = server.getProbe(ImapGuiceProbe.class).getImapPort();
        messageSender = new SMTPMessageSender("domain.tld")
            .connect("127.0.0.1", smtpPort);
        imapClient = new TestIMAPClient().connect("127.0.0.1", imapPort);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN.asString())
            .addUser(BOB.asString(), BOB_PASSWORD);

        server.getProbe(TeamMailboxProbe.class)
            .create(MARKETING_TEAM_MAILBOX)
            .addMember(MARKETING_TEAM_MAILBOX, BOB);

        PreemptiveBasicAuthScheme basicAuthScheme = new PreemptiveBasicAuthScheme();
        basicAuthScheme.setUserName(BOB.asString());
        basicAuthScheme.setPassword(BOB_PASSWORD);

        RestAssured.requestSpecification = Fixture.baseRequestSpecBuilder(server)
            .setAuth(basicAuthScheme)
            .addHeader(ACCEPT.toString(), Fixture.ACCEPT_RFC8621_VERSION_HEADER())
            .build();
    }

    @Test
    void smtpShouldAcceptSubmissionFromATeamMailbox() throws Exception {
        assertThatCode(() -> messageSender.authenticate(BOB.asString(), BOB_PASSWORD)
            .sendMessage(MARKETING_TEAM_MAILBOX.asMailAddress().asString(), BOB.asString()))
            .doesNotThrowAnyException();

        imapClient.login(BOB, BOB_PASSWORD)
            .select(INBOX)
            .awaitMessage(AWAIT_TEN_SECONDS);
    }

    @Test
    void smtpShouldAcceptSubmissionFromExtraSender(GuiceJamesServer server) throws Exception {
        Username andre = Username.fromLocalPartWithDomain("andre", DOMAIN);
        String andrePassword = "654321";
        server.getProbe(DataProbeImpl.class)
            .addUser(andre.asString(), andrePassword);

        server.getProbe(TeamMailboxProbe.class)
            .addExtraSender(MARKETING_TEAM_MAILBOX, andre);

        assertThatCode(() -> messageSender.authenticate(andre.asString(), andrePassword)
            .sendMessage(MARKETING_TEAM_MAILBOX.asMailAddress().asString(), BOB.asString()))
            .doesNotThrowAnyException();

        imapClient.login(BOB, BOB_PASSWORD)
            .select(INBOX)
            .awaitMessage(AWAIT_TEN_SECONDS);
    }

    @Test
    void teamMailboxShouldReceivedEmailFromAuthenticatedUser(GuiceJamesServer server) throws Exception {
        Username andre = Username.fromLocalPartWithDomain("andre", DOMAIN);
        String andrePassword = "123456";
        server.getProbe(DataProbeImpl.class)
            .addUser(andre.asString(), andrePassword);

        messageSender.authenticate(andre.asString(), andrePassword)
            .sendMessageWithHeaders(andre.asString(), MARKETING_TEAM_MAILBOX.asMailAddress().asString(), "From: " + andre.asString() + "\r\nsubject: test123\r\rcontent 123\r.\r");

        AWAIT_TEN_SECONDS.until(() -> server.getProbe(SpoolerProbe.class).processingFinished());

        List<String> emailIds = given()
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
        String messageId = emailIds.get(0);
        String emailGetResponse = given()
            .auth().basic(BOB.asString(), BOB_PASSWORD)
            .body(String.format("{" +
                "  \"using\": [" +
                "    \"urn:ietf:params:jmap:core\"," +
                "    \"urn:ietf:params:jmap:mail\"," +
                "    \"urn:apache:james:params:jmap:mail:shares\"]," +
                "  \"methodCalls\": [[\"Email/get\", {" +
                "      \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
                "      \"ids\": [\"%s\"]," +
                "      \"properties\":[\"subject\"]" +
                "    }, \"c1\"]]" +
                "}", messageId))
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .contentType(JSON)
            .extract()
            .body()
            .asString();

        assertThatJson(emailGetResponse)
            .whenIgnoringPaths("methodResponses[0][1].state")
            .isEqualTo(String.format("{" +
                "    \"sessionState\": \"2c9f1b12-b35a-43e6-9af2-0106fb53a943\"," +
                "    \"methodResponses\": [" +
                "        [" +
                "            \"Email/get\"," +
                "            {" +
                "                \"accountId\": \"29883977c13473ae7cb7678ef767cbfbaffc8a44a6e463d971d23a65c1dc4af6\"," +
                "                \"notFound\": []," +
                "                \"list\": [" +
                "                    {" +
                "                        \"subject\": \"test123\"," +
                "                        \"id\": \"%s\"" +
                "                    }" +
                "                ]" +
                "            }," +
                "            \"c1\"" +
                "        ]" +
                "    ]" +
                "}", messageId));
    }
}