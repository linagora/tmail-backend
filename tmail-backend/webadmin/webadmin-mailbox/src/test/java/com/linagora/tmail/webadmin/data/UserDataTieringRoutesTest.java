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

package com.linagora.tmail.webadmin.data;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.james.core.Username;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.tiering.UserDataTieringService;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

class UserDataTieringRoutesTest {

    private static final Username BOB = Username.of("bob@example.com");

    private WebAdminServer webAdminServer;
    private UserDataTieringService tieringService;

    @BeforeEach
    void setUp() {
        tieringService = mock(UserDataTieringService.class);
        when(tieringService.tierUserData(any(), any())).thenReturn(Mono.empty());

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new UserDataTieringRoutes(tieringService, new JsonTransformer()))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void postShouldReturn204OnSuccess() {
        given()
            .queryParam("tiering", "30d")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void postShouldDelegateToService() {
        given()
            .queryParam("tiering", "30d")
        .when()
            .post("/users/bob@example.com/data");

        verify(tieringService).tierUserData(eq(BOB), eq(Duration.ofDays(30)));
    }

    @Test
    void postShouldAcceptHourDuration() {
        given()
            .queryParam("tiering", "24h")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        verify(tieringService).tierUserData(eq(BOB), eq(Duration.ofHours(24)));
    }

    @Test
    void postShouldReturn400WhenTieringParamMissing() {
        when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("tiering"));

        verifyNoInteractions(tieringService);
    }

    @Test
    void postShouldReturn400WhenTieringParamBlank() {
        given()
            .queryParam("tiering", "")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("tiering"));

        verifyNoInteractions(tieringService);
    }

    @Test
    void postShouldReturn400WhenTieringParamInvalid() {
        given()
            .queryParam("tiering", "notaduration")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("notaduration"));

        verifyNoInteractions(tieringService);
    }
}
