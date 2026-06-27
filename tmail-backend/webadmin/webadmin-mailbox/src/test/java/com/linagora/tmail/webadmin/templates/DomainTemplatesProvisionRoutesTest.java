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

package com.linagora.tmail.webadmin.templates;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

class DomainTemplatesProvisionRoutesTest {
    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private TemplatesProvisionService provisionService;

    @BeforeEach
    void setUp() throws Exception {
        provisionService = mock(TemplatesProvisionService.class);
        when(provisionService.sourceFolderExists(any(), any())).thenReturn(Mono.just(true));
        when(provisionService.provisionDomain(any(), any(), any(), any(), anyInt(), any()))
            .thenReturn(Mono.just(Task.Result.COMPLETED));

        DomainList domainList = mock(DomainList.class);
        when(domainList.containsDomain(Domain.of("example.com"))).thenReturn(true);
        when(domainList.containsDomain(Domain.of("unknown.com"))).thenReturn(false);

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new DomainTemplatesProvisionRoutes(taskManager, domainList, provisionService, new JsonTransformer()),
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(DomainTemplatesProvisionTaskAdditionalInformationDTO.module())))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void postShouldReturn201WithTaskId() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/domains/example.com/templates")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void taskShouldCompleteSuccessfully() {
        String taskId = given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/domains/example.com/templates")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .getString("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("status", Matchers.is("completed"))
            .body("type", Matchers.is(DomainTemplatesProvisionTask.TASK_TYPE.asString()))
            .body("additionalInformation.domain", Matchers.is("example.com"))
            .body("additionalInformation.sourceUser", Matchers.is("templates@example.com"));
    }

    @Test
    void postShouldReturn400WhenActionIsUnknown() {
        given()
            .queryParam("action", "unknown")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/domains/example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn400WhenFromIsMissing() {
        given()
            .queryParam("action", "provision")
        .when()
            .post("/domains/example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn404WhenDomainDoesNotExist() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@unknown.com")
        .when()
            .post("/domains/unknown.com/templates")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void postShouldReturn404WhenSourceFolderDoesNotExist() {
        when(provisionService.sourceFolderExists(any(), any())).thenReturn(Mono.just(false));

        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/domains/example.com/templates")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void postShouldReturn400WhenUsersPerSecondIsInvalid() {
        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
            .queryParam("usersPerSecond", "0")
        .when()
            .post("/domains/example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }
}
