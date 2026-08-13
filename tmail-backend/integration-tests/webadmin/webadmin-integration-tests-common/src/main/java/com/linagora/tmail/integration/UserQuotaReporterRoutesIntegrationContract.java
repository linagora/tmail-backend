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

import java.util.UUID;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.integration.probe.MaxQuotaManagerProbe;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

public abstract class UserQuotaReporterRoutesIntegrationContract {
    private Domain domain;
    private Username bob;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        domain = Domain.of("quota-" + UUID.randomUUID() + ".linagora.com");
        bob = Username.fromLocalPartWithDomain("bob", domain);

        server.getProbe(DataProbeImpl.class)
            .fluent()
            .addDomain(domain.asString())
            .addUser(bob.asString(), "password");

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown(GuiceJamesServer server) throws Exception {
        MaxQuotaManagerProbe quotaProbe = server.getProbe(MaxQuotaManagerProbe.class);
        quotaProbe.removeMaxStorage(bob);
        quotaProbe.removeDomainMaxStorage(domain);
        server.getProbe(DataProbeImpl.class).removeUser(bob.asString());
    }

    @Test
    void shouldCountUsersHavingSpecificQuota(GuiceJamesServer server) throws MailboxException {
        server.getProbe(MaxQuotaManagerProbe.class)
            .setDomainMaxStorage(domain, QuotaSizeLimit.size(100));

        server.getProbe(MaxQuotaManagerProbe.class)
            .setMaxStorage(bob, QuotaSizeLimit.size(1000));

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
            .setDomainMaxStorage(domain, QuotaSizeLimit.size(100));

        server.getProbe(MaxQuotaManagerProbe.class)
            .setMaxStorage(bob, QuotaSizeLimit.size(1000));

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
                        "user": "%s",
                        "storageLimit": 1000,
                        "countLimit": null
                    }
                ]
                    """.formatted(bob.asString()));
    }

    @Test
    void shouldReturnExtraQuotaSum(GuiceJamesServer server) throws MailboxException {
        server.getProbe(MaxQuotaManagerProbe.class)
            .setDomainMaxStorage(domain, QuotaSizeLimit.size(100));

        server.getProbe(MaxQuotaManagerProbe.class)
            .setMaxStorage(bob, QuotaSizeLimit.size(1000));

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
                        "totalExtraStorageLimit": 900,
                        "totalExtraCountLimit": 0,
                        "totalUnlimitedStorage": 0,
                        "totalUnlimitedCount": 0
                    }
                    """);
    }
}
