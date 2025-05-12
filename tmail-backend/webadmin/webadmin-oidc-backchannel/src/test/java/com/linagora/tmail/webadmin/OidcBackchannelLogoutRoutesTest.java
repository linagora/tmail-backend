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

package com.linagora.tmail.webadmin;

import static io.restassured.RestAssured.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import com.linagora.tmail.james.jmap.oidc.OidcTokenCache;
import com.linagora.tmail.james.jmap.oidc.Sid;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

public class OidcBackchannelLogoutRoutesTest {
    private WebAdminServer webAdminServer;
    private OidcTokenCache oidcTokenCache;

    @BeforeEach
    void setUp() {
        oidcTokenCache = mock(OidcTokenCache.class);
        Mockito.when(oidcTokenCache.invalidate(any())).thenReturn(Mono.empty());
        webAdminServer = WebAdminUtils.createWebAdminServer(new OidcBackchannelLogoutRoutes(oidcTokenCache, new JsonTransformer())).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(OidcBackchannelLogoutRoutes.BASE_PATH)
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void postShouldReturn200WhenSidIsExtractedSuccessfully() {
        String validToken = generateJwtLikeTokenWithSid("sid1");

        given().log().ifValidationFails()
            .contentType(ContentType.URLENC)
            .formParam("logout_token", validToken)
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void postShouldInvalidateCacheWhenSidIsPresent() {
        String validToken = generateJwtLikeTokenWithSid("sid1");

        given().log().ifValidationFails()
            .contentType(ContentType.URLENC)
            .formParam("logout_token", validToken)
        .when()
            .post()
        .then()
            .statusCode(200);

        verify(oidcTokenCache, times(1)).invalidate(new Sid("sid1"));
    }

    @Test
    void postShouldReturn400WhenLogoutTokenIsMissing() {
        given()
            .log().ifValidationFails()
            .contentType(ContentType.URLENC)
        .when()
            .post()
        .then()
            .statusCode(400);
    }

    @Test
    void postShouldReturn415WhenContentTypeIsInvalid() {
        String validToken = generateJwtLikeTokenWithSid("sid1");

        ContentType invalidateContentType = ContentType.JSON;
        given()
            .log().ifValidationFails()
            .contentType(invalidateContentType)
            .body("{\"logout_token\":\"" + validToken + "\"}")
        .when()
            .post()
        .then()
            .statusCode(415);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "invalidtoken123456",
        "eyJblablabla",
        "eyJ12345.eyJzaWQiOiJzaWQxIn0."
    })
    void postShouldReturn200WhenCanNotExtractSidFromToken(String invalidToken) {
        given().log().ifValidationFails()
            .contentType(ContentType.URLENC)
            .formParam("logout_token", invalidToken)
        .when()
            .post()
        .then()
            .log().ifValidationFails()
            .statusCode(200);
    }

    @Test
    void postShouldReturn200WhenJwtHasNoSidClaim() {
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"notSid\":\"abc\"}").getBytes(StandardCharsets.UTF_8));
        String tokenWithoutSid = "eyJblablabla." + payload + ".signature";

        given()
            .log().ifValidationFails()
            .contentType(ContentType.URLENC)
            .formParam("logout_token", tokenWithoutSid)
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void postShouldReturn200WhenSidIsEmpty() {
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("{\"sid\":\"\"}".getBytes(StandardCharsets.UTF_8));
        String tokenWithEmptySid = "header." + payload + ".signature";

        given()
            .contentType(ContentType.URLENC)
            .formParam("logout_token", tokenWithEmptySid)
        .when()
            .post()
        .then()
            .statusCode(200);
    }

    @Test
    void postShouldBeIdempotentWhenSameTokenIsPostedMultipleTimes() {
        String sidValue = "sid123";
        String token = generateJwtLikeTokenWithSid(sidValue);

        for (int i = 0; i < 3; i++) {
            given()
                .contentType(ContentType.URLENC)
                .formParam("logout_token", token)
            .when()
                .post()
            .then()
                .statusCode(200);
        }

        verify(oidcTokenCache, times(3)).invalidate(new Sid(sidValue));
    }


    private String generateJwtLikeTokenWithSid(String sid) {
        String header = "eyJblablabla";
        String payload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(("{\"sid\":\"" + sid + "\"}").getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }
}
