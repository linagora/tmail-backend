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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

class UserTemplatesProvisionRoutesTest {
    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private TemplatesProvisionService provisionService;

    @BeforeEach
    void setUp() {
        provisionService = mock(TemplatesProvisionService.class);
        when(provisionService.sourceFolderExists(any(), any())).thenReturn(Mono.just(true));
        when(provisionService.provisionUser(any(), any(), any(), any(), any()))
            .thenReturn(Mono.just(Task.Result.COMPLETED));

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new UserTemplatesProvisionRoutes(taskManager, provisionService, new JsonTransformer()),
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(UserTemplatesProvisionTaskAdditionalInformationDTO.module())))
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
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void taskShouldCompleteSuccessfully() {
        String taskId = given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
            .queryParam("folderName", "Templates.Marketing")
        .when()
            .post("/users/bob@example.com/templates")
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
            .body("type", Matchers.is(UserTemplatesProvisionTask.TASK_TYPE.asString()))
            .body("additionalInformation.sourceUser", Matchers.is("templates@example.com"))
            .body("additionalInformation.targetUser", Matchers.is("bob@example.com"))
            .body("additionalInformation.folderName", Matchers.is("Templates.Marketing"));
    }

    @Test
    void postShouldReturn400WhenActionIsUnknown() {
        given()
            .queryParam("action", "unknown")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn400WhenFromIsMissing() {
        given()
            .queryParam("action", "provision")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn404WhenSourceFolderDoesNotExist() {
        when(provisionService.sourceFolderExists(any(), any())).thenReturn(Mono.just(false));

        given()
            .queryParam("action", "provision")
            .queryParam("from", "templates@example.com")
        .when()
            .post("/users/bob@example.com/templates")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }
}
