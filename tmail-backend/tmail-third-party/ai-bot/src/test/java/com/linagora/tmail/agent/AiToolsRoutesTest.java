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
package com.linagora.tmail.agent;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_OK;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.hamcrest.Matchers.equalTo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.linagora.tmail.jmap.aibot.agent.AgentToolName;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

public class AiToolsRoutesTest extends AiToolsTestBase {

    @RegisterExtension
    static JamesServerExtension jamesServerExtension = server();

    @Test
    void shouldReturn200WhenAccessingAiToolsEndpoint() throws IOException {

        String expectedBody = loadExpectedTools();
        String response = given()
            .when()
            .get()
            .then()
            .statusCode(SC_OK)
            .header("Access-Control-Allow-Origin", "*")
            .contentType("application/json")
            .extract()
            .body()
            .jsonPath()
            .prettify();

        assertThatJson(response).isEqualTo(expectedBody);
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"GET", "POST"})
    void shouldReturnErrorWhenWrongAuth(Method method, GuiceJamesServer jamesServer) {
        requestAs(jamesServer, Username.fromLocalPartWithDomain("unknown", DOMAIN), method)
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json");
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"GET", "POST"})
    void shouldRejectUserWithNoSaasAccount(Method method, GuiceJamesServer jamesServer) {
        requestAs(jamesServer, USER_WITH_NO_SAAS, method)
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json");
    }

    @ParameterizedTest
    @EnumSource(value = Method.class, names = {"GET", "POST"})
    void shouldRejectNonPayingUser(Method method, GuiceJamesServer jamesServer) {
        requestAs(jamesServer, NON_PAYING_USER, method)
            .statusCode(SC_UNAUTHORIZED)
            .contentType("application/json")
            .body("detail", equalTo("User is not eligible for AI tools"));
    }

    @Test
    void postShouldAnswerWithAToolMessage() {
        postTool(LIST_EMAILS, "{}")
            .statusCode(SC_OK)
            .header("Access-Control-Allow-Origin", "*")
            .contentType("application/json")
            .body("role", equalTo("tool"))
            .body("tool_call_id", equalTo("call_1"))
            .body("content", equalTo("{\"mailboxId\":\"" + inboxId.serialize() + "\",\"page\":1,\"limit\":10,\"emails\":[]}"));
    }

    @Test
    void shouldAcceptArgumentsAsAJsonString() {
        postTool(LIST_EMAILS, "\"{\\\"limit\\\": 5}\"")
            .statusCode(SC_OK)
            .body("content", equalTo("{\"mailboxId\":\"" + inboxId.serialize() + "\",\"page\":1,\"limit\":5,\"emails\":[]}"));
    }

    @Test
    void shouldAcceptACallWithoutArguments() {
        given()
            .contentType(ContentType.JSON)
            .body("{\"id\": \"call_1\", \"function\": {\"name\": \"list_emails\"}}")
        .when()
            .post()
        .then()
            .statusCode(SC_OK);
    }

    // The list of the tools for a GET, a call of one for a POST
    private static ValidatableResponse requestAs(GuiceJamesServer jamesServer, Username username, Method method) {
        RequestSpecification request = given(specificationOf(jamesServer, username));
        if (method == Method.POST) {
            request = request.contentType(ContentType.JSON).body(toolCall(LIST_EMAILS, "{}"));
        }
        return request.when().request(method).then();
    }

    private String loadExpectedTools() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream("expected-tools.json")) {
            return new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCalls")
    void shouldRejectAnInvalidCall(String description, String body, int status, String detail) {
        post(body)
            .statusCode(status)
            .body("detail", equalTo(detail));
    }

    static Stream<Arguments> invalidCalls() {
        return Stream.of(
            Arguments.of("shouldReturnNotFoundForAnUnknownTool", toolCall(new AgentToolName("delete_everything"), "{}"), SC_NOT_FOUND, "Unknown tool: delete_everything"),
            Arguments.of("shouldRejectABodyThatIsNotJson", "not json", SC_BAD_REQUEST, "Request body is not valid JSON"),
            Arguments.of("shouldRejectACallWithoutId", "{\"function\": {\"name\": \"list_emails\", \"arguments\": {}}}", SC_BAD_REQUEST, "Missing 'id'"),
            Arguments.of("shouldRejectACallWithoutToolName", "{\"id\": \"call_1\", \"function\": {\"arguments\": {}}}", SC_BAD_REQUEST, "Missing 'function.name'"),
            Arguments.of("shouldRejectArgumentsThatAreNotAnObject", toolCall(LIST_EMAILS, "\"[1, 2]\""), SC_BAD_REQUEST, "'function.arguments' is not a valid JSON object"));
    }
}
