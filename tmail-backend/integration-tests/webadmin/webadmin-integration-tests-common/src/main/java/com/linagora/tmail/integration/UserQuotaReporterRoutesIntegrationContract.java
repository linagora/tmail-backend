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

package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.integration.probe.MaxQuotaManagerProbe;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public abstract class UserQuotaReporterRoutesIntegrationContract {
    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username BOB = Username.of("bob@domain.tld");

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(DOMAIN.asString())
            .addUser(BOB.asString(), "password");

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void shouldCountUsersHavingSpecificQuota(GuiceJamesServer server) throws MailboxException {
        server.getProbe(MaxQuotaManagerProbe.class)
            .setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(100));

        server.getProbe(MaxQuotaManagerProbe.class)
            .setMaxStorage(BOB, QuotaSizeLimit.size(1000));

        given()
            .get("/reports/quota/users/count?hasSpecificQuota")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .body(CoreMatchers.is("1"));
    }

    @Test
    void shouldReturnUsersHavingSpecificQuota(GuiceJamesServer server) throws MailboxException {
        server.getProbe(MaxQuotaManagerProbe.class)
            .setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(100));

        server.getProbe(MaxQuotaManagerProbe.class)
            .setMaxStorage(BOB, QuotaSizeLimit.size(1000));

        String response = given()
            .get("/reports/quota/users?hasSpecificQuota")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("""
                [
                    {
                        "user": "bob@domain.tld",
                        "storageLimit": 1000,
                        "countLimit": null
                    }
                ]
                    """);
    }

    @Test
    void shouldReturnExtraQuotaSum(GuiceJamesServer server) throws MailboxException {
        server.getProbe(MaxQuotaManagerProbe.class)
            .setDomainMaxStorage(DOMAIN, QuotaSizeLimit.size(100));

        server.getProbe(MaxQuotaManagerProbe.class)
            .setMaxStorage(BOB, QuotaSizeLimit.size(1000));

        String response = given()
            .get("/reports/quota/users/sum?hasSpecificQuota")
        .then()
            .statusCode(HttpStatus.OK_200)
            .contentType(ContentType.JSON)
            .extract()
            .body().asString();

        assertThatJson(response)
            .isEqualTo("""
                    {
                        "storageLimit": 900,
                        "countLimit": 0
                    }
                    """);
    }
}
