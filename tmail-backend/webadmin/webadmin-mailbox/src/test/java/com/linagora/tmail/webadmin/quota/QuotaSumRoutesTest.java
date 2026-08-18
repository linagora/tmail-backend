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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Domain;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.model.QuotaSum;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

public class QuotaSumRoutesTest {
    private WebAdminServer webAdminServer;
    private QuotaSumDao quotaSumDao;

    @BeforeEach
    void setUp() {
        quotaSumDao = mock(QuotaSumDao.class);
        QuotaSumRoutes routes = new QuotaSumRoutes(quotaSumDao, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void getGlobalSumShouldReturnCountAndSize() {
        when(quotaSumDao.globalUsage()).thenReturn(Mono.just(new QuotaSum(37346L, 3486924565L)));

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
                    "count": 37346,
                    "size": 3486924565
                }
                """);
    }

    @Test
    void getGlobalSumShouldReturnZeroByDefault() {
        when(quotaSumDao.globalUsage()).thenReturn(Mono.just(QuotaSum.ZERO));

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
    void getDomainSumShouldReturnCountAndSizeForGivenDomain() {
        Domain domain = Domain.of("domain.tld");
        when(quotaSumDao.domainUsage(domain)).thenReturn(Mono.just(new QuotaSum(37346L, 3486924565L)));

        String response = given()
            .get("/quota/domains/" + domain.asString() + "/sum")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("""
                {
                    "count": 37346,
                    "size": 3486924565
                }
                """);
    }

    @Test
    void getDomainSumShouldReturn400WhenDomainIsInvalid() {
        given()
            .get("/quota/domains/invalid_domain!/sum")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }
}
