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

import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.DataProbeImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import com.github.fge.lambdas.Throwing;

import io.restassured.authentication.NoAuthScheme;
import io.restassured.http.Header;

public abstract class OidcAuthenticationContract {
    private static final String USERINFO_TOKEN_URI_PATH = "/oauth2/userinfo";
    private static final String INTROSPECT_TOKEN_URI_PATH = "/oauth2/introspect";
    private static final long TOKEN_EXPIRATION_TIME = Clock.systemUTC().instant().plus(Duration.ofHours(1)).getEpochSecond();

    private static final ClientAndServer mockServer = ClientAndServer.startClientAndServer(0);

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
            .addUser(BOB.asString(), BOB_PASSWORD);

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
    void shoulAuthenticateWithOidc() {
        String emailClaimValue = BOB.asString();
        updateMockServerTokenInfoResponse(emailClaimValue);

        Header authHeader = new Header(AUTHORIZATION_HEADER(), "Bearer oidc_opac_token");
        given()
            .headers(getHeadersWith(authHeader))
            .body(ECHO_REQUEST_OBJECT())
        .when()
            .post()
        .then()
            .statusCode(200);
    }
}
