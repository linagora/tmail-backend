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
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class TMailHealthCheckIntegrationTests {
    @Test
    void tmailHealthCheckShouldBeWellBinded(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        List<String> listComponentNames =
            when()
                .get("/healthcheck/checks")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .jsonPath()
                .getList("componentName", String.class);

        assertThat(listComponentNames).contains("Tasks execution", "Rspamd", "Redis");
    }

    @Test
    void tasksHealthCheckShouldReturnHealthyByDefault(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        String listComponents =
            given()
                .urlEncodingEnabled(false)
            .when()
                .get("/healthcheck/checks/Tasks%20execution")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .asString();

        assertThat(listComponents).isEqualTo("""
            {"componentName":"Tasks execution","escapedComponentName":"Tasks%20execution","status":"healthy","cause":null}""");
    }
}
