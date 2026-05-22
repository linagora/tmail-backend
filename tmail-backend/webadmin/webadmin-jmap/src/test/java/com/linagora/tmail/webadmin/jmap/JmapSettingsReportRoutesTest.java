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
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.api.DomainListException;
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
import reactor.core.publisher.Flux;

class JmapSettingsReportRoutesTest {
    private static final Domain LINAGORA = Domain.of("linagora.com");
    private static final Username BOB = Username.of("bob@linagora.com");
    private static final Username ALICE = Username.of("alice@linagora.com");
    private static final String REPORT_PATH = "/jmap/settings/reports";

    private WebAdminServer webAdminServer;
    private MemoryJmapSettingsRepository settingsRepository;
    private JmapSettingsRepositoryJavaUtils settingsUtils;
    private UsersRepository usersRepository;
    private DomainList domainList;

    @BeforeEach
    void setUp() throws Exception {
        settingsRepository = new MemoryJmapSettingsRepository();
        settingsUtils = new JmapSettingsRepositoryJavaUtils(settingsRepository);
        usersRepository = mock(UsersRepository.class);
        domainList = mock(DomainList.class);

        JmapSettingsReportRoutes routes = new JmapSettingsReportRoutes(
            settingsRepository, usersRepository, domainList, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class WithoutDomainFilter {
        @Test
        void shouldReturnEmptyWhenNoUsers() {
            when(usersRepository.listReactive()).thenReturn(Flux.empty());

            String response = when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{}");
        }

        @Test
        void shouldReturnEmptyWhenUsersHaveNoSettings() {
            when(usersRepository.listReactive()).thenReturn(Flux.just(BOB, ALICE));

            String response = when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{}");
        }

        @Test
        void shouldAggregateSettingsAcrossAllUsers() {
            when(usersRepository.listReactive()).thenReturn(Flux.just(BOB, ALICE));
            settingsUtils.reset(BOB, Map.of("language", "fr"));
            settingsUtils.reset(ALICE, Map.of("language", "fr"));

            String response = when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":{\"fr\":2}}");
        }

        @Test
        void shouldCountDistinctValuesForSameKey() {
            when(usersRepository.listReactive()).thenReturn(Flux.just(BOB, ALICE));
            settingsUtils.reset(BOB, Map.of("language", "fr"));
            settingsUtils.reset(ALICE, Map.of("language", "en"));

            String response = when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":{\"fr\":1,\"en\":1}}");
        }

        @Test
        void shouldHandleMultipleSettingsKeys() {
            when(usersRepository.listReactive()).thenReturn(Flux.just(BOB));
            settingsUtils.reset(BOB, Map.of("language", "fr", "trash.cleanup.enabled", "true"));

            String response = when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":{\"fr\":1},\"trash.cleanup.enabled\":{\"true\":1}}");
        }

        @Test
        void shouldIgnoreUsersWithNoSettings() {
            when(usersRepository.listReactive()).thenReturn(Flux.just(BOB, ALICE));
            settingsUtils.reset(BOB, Map.of("language", "fr"));

            String response = when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":{\"fr\":1}}");
        }
    }

    @Nested
    class WithDomainFilter {
        @BeforeEach
        void setUp() throws DomainListException {
            when(domainList.containsDomain(LINAGORA)).thenReturn(true);
        }

        @Test
        void shouldReturnEmptyWhenDomainHasNoUsers() {
            when(usersRepository.listUsersOfADomainReactive(LINAGORA)).thenReturn(Flux.empty());

            String response = given()
                .queryParam("domain", LINAGORA.asString())
            .when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{}");
        }

        @Test
        void shouldAggregateSettingsForDomainUsers() {
            when(usersRepository.listUsersOfADomainReactive(LINAGORA)).thenReturn(Flux.just(BOB, ALICE));
            settingsUtils.reset(BOB, Map.of("language", "fr"));
            settingsUtils.reset(ALICE, Map.of("language", "fr"));

            String response = given()
                .queryParam("domain", LINAGORA.asString())
            .when()
                .get(REPORT_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).isEqualTo("{\"language\":{\"fr\":2}}");
        }

        @Test
        void shouldReturn404ForUnknownDomain() {
            given()
                .queryParam("domain", "unknown.com")
            .when()
                .get(REPORT_PATH)
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }
}
