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

import com.linagora.tmail.mailbox.quota.QuotaSum;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

public class QuotaSumRoutesTest {
    private static final Domain DOMAIN = Domain.of("domain.tld");

    private WebAdminServer webAdminServer;
    private QuotaSumDao quotaSumDao;

    @BeforeEach
    void setUp() {
        quotaSumDao = mock(QuotaSumDao.class);
        when(quotaSumDao.globalUsage()).thenReturn(Mono.just(new QuotaSum(37346, 3486924565L)));
        when(quotaSumDao.domainUsage(DOMAIN)).thenReturn(Mono.just(new QuotaSum(37346, 3486924565L)));
        QuotaSumRoutes quotaSumRoutes = new QuotaSumRoutes(quotaSumDao, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(quotaSumRoutes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void globalSumShouldReturnCountAndSize() {
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
    void domainSumShouldReturnCountAndSize() {
        String response = given()
            .get("/quota/domains/" + DOMAIN.asString())
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
}
