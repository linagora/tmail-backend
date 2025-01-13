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
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.james.GuiceJamesServer;
import org.apache.james.core.Domain;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxProbe;

import io.restassured.RestAssured;

public abstract class RecomputeQuotaTeamMailboxesRouteIntegrationContract {
    private static final String TEAM_MAILBOX_DOMAIN = "linagora.com";
    private static final TeamMailbox MARKETING_TEAM_MAILBOX = TeamMailbox.apply(Domain.of(TEAM_MAILBOX_DOMAIN), TeamMailboxName.fromString("marketing").toOption().get());
    private static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + TEAM_MAILBOX_DOMAIN + Constants.SEPARATOR + "team-mailboxes";

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class)
                .addDomain(TEAM_MAILBOX_DOMAIN);

        server.getProbe(TeamMailboxProbe.class)
            .create(MARKETING_TEAM_MAILBOX);

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath(BASE_PATH)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void recomputeQuotaTeamMailboxesShouldBeExposed() {
        given()
            .queryParam("task", "RecomputeQuotas")
            .post()
        .then()
            .statusCode(201)
            .body("taskId", notNullValue());
    }

    @Test
    void recomputeQuotaTeamMailboxesTaskShouldWork() {
        String taskId = given()
            .queryParam("task", "RecomputeQuotas")
            .post()
            .jsonPath()
            .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("startedDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("type", is("recompute-quota-team-mailboxes"))
            .body("type", is("recompute-quota-team-mailboxes"))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("recompute-quota-team-mailboxes"))
            .body("additionalInformation.domain", is("linagora.com"))
            .body("additionalInformation.failedQuotaRoots", is(empty()))
            .body("additionalInformation.processedQuotaRoots", is(1));
    }
}
