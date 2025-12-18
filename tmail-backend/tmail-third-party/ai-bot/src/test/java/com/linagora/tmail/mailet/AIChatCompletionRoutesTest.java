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

package com.linagora.tmail.mailet;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.james.data.UsersRepositoryModuleChooser.Implementation.DEFAULT;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.authScheme;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.jmap.http.UserCredential;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.extension.WireMockRagServerExtension;
import com.linagora.tmail.james.app.MemoryConfiguration;
import com.linagora.tmail.james.app.MemoryServer;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.specification.RequestSpecification;

class AIChatCompletionRoutesTest {
    private static final String JMAP_CHAT_COMPLETIONS_ENDPOINT = "/ai/v1/chat/completions";

    @RegisterExtension
    static WireMockRagServerExtension wireMockRagServerExtension = new WireMockRagServerExtension();

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = new JamesServerBuilder<MemoryConfiguration>(tmpDir ->
            MemoryConfiguration.builder()
                .workingDirectory(tmpDir)
                .configurationFromClasspath()
                .usersRepository(DEFAULT)
                .build())
            .server(configuration -> MemoryServer.createServer(configuration)
                .overrideWith(new LinagoraTestJMAPServerModule()))
            .extension(wireMockRagServerExtension)
            .build();

    @BeforeEach
    void setUp(GuiceJamesServer jamesServer) throws Exception {
        jamesServer.getProbe(DataProbeImpl.class).fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD);

        requestSpecification = baseRequestSpecBuilder(jamesServer)
            .setBasePath(JMAP_CHAT_COMPLETIONS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(BOB, BOB_PASSWORD)))
            .build();
    }

    @Test
    void jmapAiChatCompletionsRouteShouldForwardPayloadAndHeadersAndReturnExactResponse() {
        String resultBody = """
            {
              "id": "1234",
              "choices": [
                {
                  "finish_reason": "stop",
                  "index": 0,
                  "message": {
                    "content": "The glass green worm around Anvers"
                  }
                }
              ]
            }
            """;

        wireMockRagServerExtension.setChatCompletionResponse(200, resultBody);

        String incomingPayload = """
            {
               "messages" : [
                 {"role": "system", "content": "You are a translator."},
                 {"role": "user", "content": "Translate this text:"},
                 {"role": "user", "content": "Le ver vert en verre vers Anvers"}
               ],
                "metadata": {
                 "action": "translate"
               },
               "stream": false
            }""";

        String response = given()
            .body(incomingPayload)
        .when()
            .post()
        .then()
            .statusCode(SC_OK)
            .header("Access-Control-Allow-Origin", "*")
            .contentType("application/json")
            .extract()
            .body()
            .jsonPath()
            .prettify();

        assertThatJson(response).isEqualTo(resultBody);
    }

    @Test
    void jmapAiChatCompletionsRouteShouldReturnExactErrorResponses() {
        String resultBody = """
            {
              "details": "Internal server error..."
            }
            """;

        String incomingPayload = "{bad_payload}";

        wireMockRagServerExtension.setChatCompletionResponse(500, resultBody);

        String response = given()
            .body(incomingPayload)
        .when()
            .post()
        .then()
            .statusCode(SC_INTERNAL_SERVER_ERROR)
            .contentType("application/json")
            .extract()
            .body()
            .jsonPath()
            .prettify();

        assertThatJson(response).isEqualTo(resultBody);
    }

    @Test
    void jmapAiChatCompletionsRouteShouldReturnErrorWhenWrongAuth(GuiceJamesServer jamesServer) {
        String incomingPayload = "{test_wrong_auth}";

        RequestSpecification requestSpecification = baseRequestSpecBuilder(jamesServer)
            .setBasePath(JMAP_CHAT_COMPLETIONS_ENDPOINT)
            .setAuth(authScheme(new UserCredential(ALICE, ALICE_PASSWORD)))
            .build();

        given(requestSpecification)
            .body(incomingPayload)
        .when()
            .post()
        .then()
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json");
    }
}
