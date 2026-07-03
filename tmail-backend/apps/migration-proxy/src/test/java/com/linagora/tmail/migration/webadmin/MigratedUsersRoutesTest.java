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

package com.linagora.tmail.migration.webadmin;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

import org.apache.james.core.Username;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.migration.core.MemoryMigratedUsersRepository;
import com.linagora.tmail.migration.core.MigratedUsersRepository;
import com.linagora.tmail.migration.core.ProxyConnectionRegistry;

import io.restassured.RestAssured;

class MigratedUsersRoutesTest {
    private static final String BOB = "bob@domain.tld";
    private static final String ALICE = "alice@domain.tld";

    private WebAdminServer webAdminServer;
    private MigratedUsersRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MemoryMigratedUsersRepository();
        webAdminServer = WebAdminUtils.createWebAdminServer(
                new MigratedUsersRoutes(repository, new ProxyConnectionRegistry(), new JsonTransformer()))
            .start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void getShouldReturnEmptyByDefault() {
        given()
            .get("/migratedUsers")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body(".", hasSize(0));
    }

    @Test
    void putShouldMarkUserAsMigrated() {
        given()
            .put("/migratedUsers/" + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(repository.isMigrated(Username.of(BOB)).block()).isTrue();
    }

    @Test
    void getShouldListMigratedUsers() {
        given().put("/migratedUsers/" + BOB).then().statusCode(HttpStatus.NO_CONTENT_204);

        given()
            .get("/migratedUsers")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body(".", contains(BOB));
    }

    @Test
    void headShouldReturnNoContentWhenMigrated() {
        given().put("/migratedUsers/" + BOB);

        given()
            .head("/migratedUsers/" + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);
    }

    @Test
    void headShouldReturnNotFoundWhenNotMigrated() {
        given()
            .head("/migratedUsers/" + ALICE)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void headShouldReturnBadRequestWhenUsernameIsInvalid() {
        given()
            .head("/migratedUsers/invalid@user@domain.tld")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void deleteShouldUnmarkUser() {
        given().put("/migratedUsers/" + BOB).then().statusCode(HttpStatus.NO_CONTENT_204);

        given()
            .delete("/migratedUsers/" + BOB)
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(repository.isMigrated(Username.of(BOB)).block()).isFalse();
    }
}
