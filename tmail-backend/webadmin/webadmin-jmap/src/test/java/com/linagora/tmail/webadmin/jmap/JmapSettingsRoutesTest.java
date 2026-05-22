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

package com.linagora.tmail.webadmin.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.settings.JmapSettingsRepositoryJavaUtils;
import com.linagora.tmail.james.jmap.settings.MemoryJmapSettingsRepository;

import io.restassured.RestAssured;

class JmapSettingsRoutesTest {
    private static final Username BOB = Username.of("bob@linagora.com");
    private static final String BOB_SETTINGS_PATH = "/users/bob@linagora.com/jmap/settings";

    private WebAdminServer webAdminServer;
    private MemoryJmapSettingsRepository settingsRepository;
    private JmapSettingsRepositoryJavaUtils settingsUtils;
    private UsersRepository usersRepository;

    @BeforeEach
    void setUp() throws Exception {
        settingsRepository = new MemoryJmapSettingsRepository();
        settingsUtils = new JmapSettingsRepositoryJavaUtils(settingsRepository);
        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(BOB)).thenReturn(true);

        JmapSettingsRoutes routes = new JmapSettingsRoutes(
            settingsRepository, usersRepository, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class GetSettings {
        @Test
        void shouldReturnEmptyMapWhenNoSettings() {
            String response = when()
                .get(BOB_SETTINGS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{}");
        }

        @Test
        void shouldReturnStoredSettings() {
            settingsUtils.reset(BOB, Map.of("language", "fr", "trash.cleanup.enabled", "true"));

            String response = when()
                .get(BOB_SETTINGS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":\"fr\",\"trash.cleanup.enabled\":\"true\"}");
        }

        @Test
        void shouldReturn404WhenUserDoesNotExist() throws Exception {
            when(usersRepository.contains(Username.of("unknown@linagora.com"))).thenReturn(false);

            when()
                .get("/users/unknown@linagora.com/jmap/settings")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }

    @Nested
    class PutSettings {
        @Test
        void shouldReturn204OnSuccess() {
            given()
                .contentType(JSON)
                .body("{\"language\":\"fr\"}")
            .when()
                .put(BOB_SETTINGS_PATH)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void shouldPersistSettings() {
            given()
                .contentType(JSON)
                .body("{\"language\":\"fr\",\"trash.cleanup.enabled\":\"true\"}")
            .when()
                .put(BOB_SETTINGS_PATH);

            String response = when()
                .get(BOB_SETTINGS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":\"fr\",\"trash.cleanup.enabled\":\"true\"}");
        }

        @Test
        void shouldReplaceExistingSettings() {
            settingsUtils.reset(BOB, Map.of("language", "fr", "trash.cleanup.enabled", "true"));

            given()
                .contentType(JSON)
                .body("{\"language\":\"en\"}")
            .when()
                .put(BOB_SETTINGS_PATH);

            String response = when()
                .get(BOB_SETTINGS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":\"en\"}");
        }

        @Test
        void shouldReturn204ForEmptyBody() {
            given()
                .contentType(JSON)
                .body("{}")
            .when()
                .put(BOB_SETTINGS_PATH)
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void shouldReturn400ForInvalidSettingsKey() {
            given()
                .contentType(JSON)
                .body("{\"invalid key with spaces\": \"value\"}")
            .when()
                .put(BOB_SETTINGS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void shouldReturn404WhenUserDoesNotExist() throws Exception {
            when(usersRepository.contains(Username.of("unknown@linagora.com"))).thenReturn(false);

            given()
                .contentType(JSON)
                .body("{\"language\":\"fr\"}")
            .when()
                .put("/users/unknown@linagora.com/jmap/settings")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }
}
