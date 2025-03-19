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

package com.linagora.calendar.app;


import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static io.restassured.config.RestAssuredConfig.newConfig;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.jwt.introspection.IntrospectionEndpoint;
import org.apache.james.util.Port;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.linagora.calendar.app.modules.CalendarDataProbe;
import com.linagora.calendar.restapi.RestApiServerProbe;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;

class TwakeCalendarGuiceServerTest  {
    public static final Domain DOMAIN = Domain.of("linagora.com");
    public static final String PASSWORD = "secret";
    public static final Username USERNAME = Username.of("btellier@linagora.com");
    private static final String INTROSPECTION_TOKEN_URI_PATH = "/token/introspect";

    private static ClientAndServer mockServer = ClientAndServer.startClientAndServer(0);

    private static IntrospectionEndpoint getIntrospectionTokenEndpoint() {
        try {
            return new IntrospectionEndpoint(new URI(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), INTROSPECTION_TOKEN_URI_PATH)).toURL(),
                Optional.empty());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @RegisterExtension
    static TwakeCalendarExtension twakeCalendarExtension = new TwakeCalendarExtension(TwakeCalendarConfiguration.builder()
        .configurationFromClasspath()
        .userChoice(TwakeCalendarConfiguration.UserChoice.MEMORY),
        binder -> binder.bind(IntrospectionEndpoint.class).toProvider(TwakeCalendarGuiceServerTest::getIntrospectionTokenEndpoint));

    @BeforeEach
    void setUp(TwakeCalendarGuiceServer server) {
        Port port = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();

        RestAssured.requestSpecification =  new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(port.getValue())
            .setBasePath("/")
            .build();

        server.getProbe(CalendarDataProbe.class).addDomain(DOMAIN)
            .addUser(USERNAME, PASSWORD);
    }

    @Test
    void shouldExposeWebAdminHealthcheck() {
        String body = given()
        .when()
            .get("/healthcheck")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            {
                "status": "healthy",
                "checks": [
                    {
                        "componentName": "Guice application lifecycle",
                        "escapedComponentName": "Guice%20application%20lifecycle",
                        "status": "healthy",
                        "cause": null
                    }
                ]
            }""");
    }

    @Test
    void shouldExposeWebAdminDomains() {
        String body = given()
        .when()
            .get("/domains")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            ["localhost","linagora.com"]""");
    }

    @Test
    void shouldExposeWebAdminUsers() {
        String body = given()
        .when()
            .get("/users")
        .then()
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("""
            [{"username":"btellier@linagora.com"}]""");
    }

    @Test
    void shouldExposeMetrics() {
        String body = given()
        .when()
            .get("/metrics")
        .then()
            .extract()
            .body()
            .asString();

        assertThat(body).contains("jvm_threads_runnable_count");
    }

    @Test
    void shouldExposeCalendarRestApi(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .get("/api/theme/anything")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThatJson(body).isEqualTo("{\"logos\":{},\"colors\":{}}");
    }

    @Test
    void shouldGenerateTokens(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .post("/api/jwt/generate")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();

        assertThat(body).startsWith("\"eyJ");
    }

    @Test
    void shouldAuthenticateWithTokens(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        String body = given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .post("/api/jwt/generate")
        .then()
            .statusCode(200)
            .extract()
            .body()
            .asString();
        String unquoted = body.substring(1, body.length() - 1);

        given()
            .header("Authorization", "Bearer " + unquoted)
        .when()
            .get("/api/theme/anything")
        .then()
            .statusCode(200);
    }

    @Test
    void shouldAuthenticateWithOidc(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);
        String activeResponse = "{" +
            "    \"exp\": 1652868271," +
            "    \"nbf\": 0," +
            "    \"iat\": 1652867971," +
            "    \"jti\": \"41ee3cc3-b908-4870-bff2-34b895b9fadf\"," +
            "    \"aud\": \"account\"," +
            "    \"typ\": \"Bearer\"," +
            "    \"acr\": \"1\"," +
            "    \"scope\": \"email\"," +
            "    \"email\": \"btellier@linagora.com\"," +
            "    \"active\": true" +
            "}";
        updateMockerServerSpecifications(activeResponse, 200);

        given()
            .header("Authorization", "Bearer oidc_opac_token")
        .when()
            .get("/api/theme/anything")
        .then()
            .statusCode(200);
    }

    @Test
    void shouldExposeLogoEndpoint(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .auth().preemptive().basic(USERNAME.asString(), PASSWORD)
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(200); // Follows the redirect;
    }

    @Test
    void shouldRejectNonExistingUsers(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .auth().basic("notFound@linagora.com", PASSWORD)
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(401); // Follows the redirect;
    }

    @Test
    void shouldRejectBadPassword(TwakeCalendarGuiceServer server) {
        targetRestAPI(server);

        given()
            .auth().basic(USERNAME.asString(), "notGood")
            .when()
            .get("/api/themes/anything/logo")
        .then()
            .statusCode(401); // Follows the redirect;
    }

    @Test
    void shouldProxyCalls(TwakeCalendarGuiceServer server) {
        // Manually tested for real content with my Linagora credentials
        targetRestAPI(server);

        given()
            .when()
            .get("/api/user/profile")
        .then()
            .statusCode(401); // Not authenticated on openpaas.linagora.com
    }

    private static void targetRestAPI(TwakeCalendarGuiceServer server) {
        RestAssured.requestSpecification =  new RequestSpecBuilder()
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .setConfig(newConfig().encoderConfig(encoderConfig().defaultContentCharset(StandardCharsets.UTF_8)))
            .setPort(server.getProbe(RestApiServerProbe.class).getPort().getValue())
            .setBasePath("/")
            .build();
    }

    private void updateMockerServerSpecifications(String response, int statusResponse) {
        mockServer
            .when(HttpRequest.request().withPath(INTROSPECTION_TOKEN_URI_PATH))
            .respond(HttpResponse.response().withStatusCode(statusResponse)
                .withHeader("Content-Type", "application/json")
                .withBody(response, StandardCharsets.UTF_8));
    }
}
