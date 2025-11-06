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
 *******************************************************************/

package com.linagora.tmail.webadmin.quota;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.CoreMatchers.is;

import java.util.Map;
import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.memory.MemoryUserQuotaReporter;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.core.Option;

public class UserQuotaReporterRoutesTest {
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Domain DOMAIN_2 = Domain.of("domain2.tld");
    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Username ALICE = Username.of("alice@domain.tld");
    private static final Username ANDRE_AT_DOMAIN_2 = Username.of("andre@domain2.tld");
    private static final QuotaRoot BOB_QUOTA_ROOT = QuotaRoot.quotaRoot(BOB.asString(), Optional.of(DOMAIN));
    private static final QuotaRoot ALICE_QUOTA_ROOT = QuotaRoot.quotaRoot(ALICE.asString(), Optional.of(DOMAIN));
    private static final QuotaRoot ANDRE_AT_DOMAIN_2_QUOTA_ROOT = QuotaRoot.quotaRoot(ANDRE_AT_DOMAIN_2.asString(), Optional.of(DOMAIN_2));

    private WebAdminServer webAdminServer;
    private InMemoryPerUserMaxQuotaManager maxQuotaManager;

    @BeforeEach
    void setUp() {
        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        UserQuotaReporter userQuotaReporter = new MemoryUserQuotaReporter(maxQuotaManager);
        UserQuotaReporterRoutes userQuotaReporterRoutes = new UserQuotaReporterRoutes(userQuotaReporter, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(userQuotaReporterRoutes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class CountUsersWithSpecificQuota {
        @Test
        void shouldCountZeroUsersHavingSpecificQuotaByDefault() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            given()
                .get("/quota/users/count?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(is("0"));
        }

        @Test
        void shouldCountUsersHavingSpecificQuota() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
            maxQuotaManager.setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.size(10001));

            given()
                .get("/quota/users/count?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(is("2"));
        }

        @Test
        void shouldCountUsersHavingUnlimitedQuota() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
            maxQuotaManager.setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.unlimited());

            given()
                .get("/quota/users/count?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(is("2"));
        }

        @Test
        void shouldFailWhenMissingHasSpecificQuotaParameter() {
            Map<String, Object> errors = given()
                .get("/quota/users/count")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("details", "'hasSpecificQuota' query parameter is missing");
        }
    }

    @Nested
    class ListUsersWithSpecificQuota {
        @Test
        void shouldReturnEmptyUsersHavingSpecificQuotaByDefault() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            given()
                .get("/quota/users?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .body(is("[]"));
        }

        @Test
        void shouldReturnUsersHavingSpecificQuota() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(1001));
            maxQuotaManager.setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(101));

            String response = given()
                .get("/quota/users?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("""
                    [
                        {
                            "user": "bob@domain.tld",
                            "storageLimit": 1001,
                            "countLimit": null
                        },
                        {
                            "user": "alice@domain.tld",
                            "storageLimit": null,
                            "countLimit": 101
                        }
                    ]
                    """);
        }

        @Test
        void shouldReturnUsersHavingUnlimitedQuota() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
            maxQuotaManager.setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.unlimited());

            String response = given()
                .get("/quota/users?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("""
                    [
                        {
                            "user": "bob@domain.tld",
                            "storageLimit": -1,
                            "countLimit": -1
                        }
                    ]
                    """);
        }

        @Test
        void shouldReturnUsersHavingSpecificQuotaAcrossDomains() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));
            maxQuotaManager.setDomainMaxStorage(DOMAIN_2, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN_2, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(1001));
            maxQuotaManager.setMaxMessage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaCountLimit.count(101));

            String response = given()
                .get("/quota/users?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .when(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("""
                    [
                        {
                            "user": "bob@domain.tld",
                            "storageLimit": 1001,
                            "countLimit": null
                        },
                        {
                            "user": "andre@domain2.tld",
                            "storageLimit": null,
                            "countLimit": 101
                        }
                    ]
                    """);
        }

        @Test
        void shouldFailWhenMissingHasSpecificQuotaParameter() {
            Map<String, Object> errors = given()
                .get("/quota/users")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("details", "'hasSpecificQuota' query parameter is missing");
        }
    }

    @Nested
    class SumUsersExtraQuota {
        @Test
        void shouldReturnZeroExtraQuotaByDefault() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            String response = given()
                .get("/quota/users/sum?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .isEqualTo("""
                    {
                        "storageLimit": 0,
                        "countLimit": 0
                    }
                    """);
        }

        @Test
        void shouldReturnExtraQuota() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(100));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(200));
            maxQuotaManager.setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(200));
            maxQuotaManager.setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.size(300));
            maxQuotaManager.setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(301));

            String response = given()
                .get("/quota/users/sum?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .isEqualTo("""
                    {
                        "storageLimit": 300,
                        "countLimit": 301
                    }
                    """);
        }

        @Test
        void shouldReturnExtraQuotaAcrossDomains() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(101));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(101));
            maxQuotaManager.setDomainMaxStorage(DOMAIN_2, QuotaSizeLimit.size(201));
            maxQuotaManager.setDomainMaxMessage(DOMAIN_2, QuotaCountLimit.count(201));

            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(201));
            maxQuotaManager.setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(201));
            maxQuotaManager.setMaxStorage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaSizeLimit.size(301));
            maxQuotaManager.setMaxMessage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaCountLimit.count(301));

            String response = given()
                .get("/quota/users/sum?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .isEqualTo("""
                    {
                        "storageLimit": 200,
                        "countLimit": 200
                    }
                    """);
        }

        @Test
        void shouldReturnUnlimitedExtraQuotaWhenUserQuotaIsUnlimited() {
            maxQuotaManager.setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(1000));
            maxQuotaManager.setDomainMaxMessage(DOMAIN, QuotaCountLimit.count(100));

            maxQuotaManager.setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.size(2000));
            maxQuotaManager.setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(200));
            maxQuotaManager.setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
            maxQuotaManager.setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.unlimited());

            String response = given()
                .get("/quota/users/sum?hasSpecificQuota")
            .then()
                .statusCode(HttpStatus.OK_200)
                .contentType(ContentType.JSON)
                .extract()
                .body().asString();

            assertThatJson(response)
                .isEqualTo("""
                    {
                        "storageLimit": -1,
                        "countLimit": -1
                    }
                    """);
        }

        @Test
        void shouldFailWhenMissingHasSpecificQuotaParameter() {
            Map<String, Object> errors = given()
                .get("/quota/users/sum")
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("details", "'hasSpecificQuota' query parameter is missing");
        }
    }

}
