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

package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import java.util.UUID;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.integration.probe.QuotaUsageProbe;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public abstract class QuotaSumRoutesIntegrationContract {
    private Domain domain1;
    private Domain domain2;
    private Username bob;
    private Username alice;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        domain1 = Domain.of("quota-" + UUID.randomUUID() + ".linagora.com");
        domain2 = Domain.of("quota-" + UUID.randomUUID() + ".linagora.com");
        bob = Username.fromLocalPartWithDomain("bob", domain1);
        alice = Username.fromLocalPartWithDomain("alice", domain2);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(domain1.asString())
            .addDomain(domain2.asString())
            .addUser(bob.asString(), "password")
            .addUser(alice.asString(), "password");

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).removeUser(bob.asString());
        server.getProbe(DataProbeImpl.class).removeUser(alice.asString());
        server.getProbe(DataProbeImpl.class).removeDomain(domain1.asString());
        server.getProbe(DataProbeImpl.class).removeDomain(domain2.asString());
    }

    @Test
    void shouldReturnZeroSumByDefault() {
        String response = given()
            .get("/quota/sum")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("""
                {
                    "count": 0,
                    "size": 0
                }
                """);
    }

    @Test
    void shouldReturnGlobalSum(GuiceJamesServer server) {
        server.getProbe(QuotaUsageProbe.class).setCurrentQuotas(bob, 5, 500);
        server.getProbe(QuotaUsageProbe.class).setCurrentQuotas(alice, 3, 300);

        String response = given()
            .get("/quota/sum")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("""
                {
                    "count": 8,
                    "size": 800
                }
                """);
    }

    @Test
    void shouldReturnDomainSum(GuiceJamesServer server) {
        server.getProbe(QuotaUsageProbe.class).setCurrentQuotas(bob, 5, 500);
        server.getProbe(QuotaUsageProbe.class).setCurrentQuotas(alice, 3, 300);

        String bobResponse = given()
            .get("/quota/domains/" + domain1.asString())
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(bobResponse)
            .isEqualTo("""
                {
                    "count": 5,
                    "size": 500
                }
                """);

        String aliceResponse = given()
            .get("/quota/domains/" + domain2.asString())
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(aliceResponse)
            .isEqualTo("""
                {
                    "count": 3,
                    "size": 300
                }
                """);
    }

    @Test
    void shouldReturnZeroForUnknownDomain() {
        String response = given()
            .get("/quota/domains/unknown-" + UUID.randomUUID() + ".linagora.com")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("""
                {
                    "count": 0,
                    "size": 0
                }
                """);
    }
}
