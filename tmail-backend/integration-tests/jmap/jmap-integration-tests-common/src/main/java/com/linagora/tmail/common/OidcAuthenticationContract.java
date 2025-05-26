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

package com.linagora.tmail.common;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.requestSpecification;
import static io.restassured.RestAssured.with;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE;
import static org.apache.james.jmap.JMAPTestingConstants.ALICE_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.BOB;
import static org.apache.james.jmap.JMAPTestingConstants.BOB_PASSWORD;
import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.apache.james.jmap.rfc8621.contract.Fixture.AUTHORIZATION_HEADER;
import static org.apache.james.jmap.rfc8621.contract.Fixture.ECHO_REQUEST_OBJECT;
import static org.apache.james.jmap.rfc8621.contract.Fixture.baseRequestSpecBuilder;
import static org.apache.james.jmap.rfc8621.contract.Fixture.getHeadersWith;

import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Username;
import org.apache.james.jmap.core.AccountId;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.james.jmap.oidc.OidcAuthenticationStrategy;

import io.restassured.authentication.NoAuthScheme;
import io.restassured.http.Header;

public abstract class OidcAuthenticationContract {
    private static final String USERINFO_TOKEN_URI_PATH = "/oauth2/userinfo";
    private static final String INTROSPECT_TOKEN_URI_PATH = "/oauth2/introspect";
    private static final String EMAIL_CLAIM_VALUE = BOB.asString();
    private static final Header AUTH_HEADER = new Header(AUTHORIZATION_HEADER(), "Bearer oidc_opac_token");
    private static final long TOKEN_EXPIRATION_TIME = Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond();

    private static final ClientAndServer mockServer = ClientAndServer.startClientAndServer(0);

    protected static final Optional<List<String>>  OIDC_AUTHENTICATION_STRATEGY = Optional.of(List.of(OidcAuthenticationStrategy.class.getCanonicalName()));

    protected static URL getUserInfoTokenEndpoint() {
        return Throwing.supplier(() -> URI.create(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), USERINFO_TOKEN_URI_PATH)).toURL()).get();
    }

    protected static URL getIntrospectTokenEndpoint() {
        return Throwing.supplier(() -> URI.create(String.format("http://127.0.0.1:%s%s", mockServer.getLocalPort(), INTROSPECT_TOKEN_URI_PATH)).toURL()).get();
    }

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN)
            .addUser(BOB.asString(), BOB_PASSWORD)
            .addUser(ALICE.asString(), ALICE_PASSWORD);

        requestSpecification = baseRequestSpecBuilder(server)
            .setAuth(new NoAuthScheme())
            .build();
    }

    @AfterEach
    void afterEach() {
        mockServer.reset();
    }

    private void updateMockerServerSpecifications(String path, String response, int statusResponse) {
        mockServer
            .when(HttpRequest.request().withPath(path))
            .respond(HttpResponse.response().withStatusCode(statusResponse)
                .withHeader("Content-Type", "application/json")
                .withBody(response, StandardCharsets.UTF_8));
    }

    private void updateMockServerTokenInfoResponse(String emailClaimValue) {
        updateMockServerUserInfoResponse(emailClaimValue);
        updateMockServerIntrospectionResponse();
    }

    private void updateMockServerUserInfoResponse(String emailClaimValue) {
        updateMockerServerSpecifications(USERINFO_TOKEN_URI_PATH, """
            {
              "sub": "twake-mail-dev",
              "email": "%s",
              "family_name": "twake-mail-dev",
              "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
              "name": "twake-mail-dev"
            }""".formatted(emailClaimValue), 200);
    }

    private void updateMockServerIntrospectionResponse() {
        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": "tmail",
                "sub": "twake-mail-dev",
                "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);
    }

    private void updateMockServerIntrospectionResponseWithAudArray() {
        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": ["tmail"],
                "sub": "twake-mail-dev",
                "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);
    }

    @Test
    void shouldAuthenticateWithOidc() {
        updateMockServerTokenInfoResponse(EMAIL_CLAIM_VALUE);

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void shouldAcceptAudArray() {
        updateMockServerTokenInfoResponse(EMAIL_CLAIM_VALUE);
        updateMockServerIntrospectionResponseWithAudArray();

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void shouldRejectOutdatedToken() {
        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);

        long expiredTime = Clock.systemUTC().instant().minus(Duration.ofHours(1)).getEpochSecond();
        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": "tmail",
                "sub": "twake-mail-dev",
                "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
                "iss": "https://sso.linagora.com"
              }""".formatted(expiredTime), 200);

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectBadAudience() {
        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);

        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": "bad",
                "sub": "twake-mail-dev",
                "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);


        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(401);
    }

    @Test
    void shouldAcceptOtherConfiguredAudience() {
        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);

        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": "james",
                "sub": "twake-mail-dev",
                "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);


        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void shouldAcceptNoSidInUserInfo() {
        String activeResponseNoSid = """
            {
              "sub": "twake-mail-dev",
              "email": "%s",
              "family_name": "twake-mail-dev",
              "name": "twake-mail-dev"
            }""".formatted(EMAIL_CLAIM_VALUE);

        updateMockerServerSpecifications(USERINFO_TOKEN_URI_PATH, activeResponseNoSid, 200);
        updateMockServerIntrospectionResponse();

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void shouldAcceptNoSidInIntrospection() {
        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);

        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": "tmail",
                "sub": "twake-mail-dev",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void shouldAcceptMissingAudInIntrospectionResponse() {
        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);

        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "sub": "twake-mail-dev",
                "sid": "dT/8+UDx1lWp1bRZkdhbS1i6ZfYhf8+bWAZQs8p0T/c",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);

        // verify the second call which uses the cache would still be fine
        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void shouldRejectWhenUserInfoFails() {
        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, """
            {
                "exp": %d,
                "scope": "openid email profile",
                "client_id": "tmail",
                "active": true,
                "aud": "tmail",
                "sub": "twake-mail-dev",
                "iss": "https://sso.linagora.com"
              }""".formatted(TOKEN_EXPIRATION_TIME), 200);

        updateMockerServerSpecifications(USERINFO_TOKEN_URI_PATH, "activeResponse", 401);

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(401);
    }

    @Test
    void shouldRejectWhenIntrospectionFails() {
        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);
        updateMockerServerSpecifications(INTROSPECT_TOKEN_URI_PATH, "", 401);

        given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(401);
    }

    @Test
    void shouldCacheResponse() {
        updateMockServerTokenInfoResponse(EMAIL_CLAIM_VALUE);

        for (int i = 0; i < 3; i++) {
            with()
                .headers(getHeadersWith(AUTH_HEADER))
                .body(ECHO_REQUEST_OBJECT())
            .when()
                .post()
            .then()
                .statusCode(200);
        }

        mockServer.verify(HttpRequest.request().withPath(INTROSPECT_TOKEN_URI_PATH),
            VerificationTimes.exactly(1));

        mockServer.verify(HttpRequest.request().withPath(USERINFO_TOKEN_URI_PATH),
            VerificationTimes.exactly(1));
    }

    @Test
    void shouldNotShareCacheAcrossDifferentOidcTokens() {
        updateMockServerIntrospectionResponse();

        updateMockServerUserInfoResponse(EMAIL_CLAIM_VALUE);
        String token1 = "Bearer token1";

        given()
            .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER(), token1)))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);

        updateMockServerUserInfoResponse(ALICE.asString());

        String token2 = "Bearer token2";
        given()
            .headers(getHeadersWith(new Header(AUTHORIZATION_HEADER(), token2)))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);

        mockServer.verify(
            HttpRequest.request()
                .withPath(USERINFO_TOKEN_URI_PATH)
                .withHeader("Authorization", token1),
            VerificationTimes.exactly(1));

        mockServer.verify(
            HttpRequest.request()
                .withPath(USERINFO_TOKEN_URI_PATH)
                .withHeader("Authorization", token2),
            VerificationTimes.exactly(1));
    }

    @Test
    void forwardSetShouldSucceedWithOidcAuthenticationStrategy() {
        AccountId accountId = AccountId.from(Username.of(EMAIL_CLAIM_VALUE)).toOption().get();

        String request = """
            {
                "using": [ "urn:ietf:params:jmap:core",
                           "com:linagora:params:jmap:forward" ],
                "methodCalls": [
                  ["Forward/set", {
                    "accountId": "%s",
                    "update": {
                        "singleton": {
                            "localCopy": true,
                            "forwards": [
                                "targetA@domain.org",
                                "targetB@domain.org"
                            ]
                        }
                    }
                  }, "c1"]
                ]
            }""".formatted(accountId.id());

        updateMockServerTokenInfoResponse(EMAIL_CLAIM_VALUE);

        String response = given()
            .headers(getHeadersWith(AUTH_HEADER))
            .body(request)
        .when()
            .post()
        .then()
            .log().ifValidationFails()
            .statusCode(200)
            .contentType("application/json")
            .extract()
            .body()
            .asString();

        assertThatJson(response).isEqualTo("""
            {
              "sessionState": "${json-unit.ignore}",
              "methodResponses": [
                ["Forward/set", {
                  "accountId": "%s",
                  "newState": "${json-unit.ignore}",
                  "updated": {"singleton":{}}
                }, "c1"]
              ]
            }
            """.formatted(accountId.id()));
    }
}
