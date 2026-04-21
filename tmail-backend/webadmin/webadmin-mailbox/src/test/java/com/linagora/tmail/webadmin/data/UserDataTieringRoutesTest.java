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

package com.linagora.tmail.webadmin.data;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.apache.james.server.task.json.dto.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.tiering.UserDataTieringService;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

class UserDataTieringRoutesTest {

    private WebAdminServer webAdminServer;
    private UserDataTieringService tieringService;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void setUp() {
        tieringService = mock(UserDataTieringService.class);
        org.mockito.Mockito.when(tieringService.tierUserData(any(), any(), any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(Mono.empty());

        taskManager = new MemoryTaskManager(new Hostname("foo"));

        webAdminServer = WebAdminUtils.createWebAdminServer(
                new UserDataTieringRoutes(tieringService, taskManager, new JsonTransformer()),
                new TasksRoutes(taskManager, new JsonTransformer(),
                    DTOConverter.of(UserDataTieringTaskAdditionalInformationDTO.module())))
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
        String taskId = given()
            .queryParam("tiering", "30d")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue())
            .extract()
            .jsonPath()
            .getString("taskId");

        assertThat(taskId).isNotEmpty();
    }

    @Test
    void taskShouldCompleteSuccessfully() {
        String taskId = given()
            .queryParam("tiering", "30d")
        .when()
            .post("/users/bob@example.com/data")
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
            .body("type", Matchers.is(UserDataTieringTask.TASK_TYPE.asString()));
    }

    @Test
    void postShouldAcceptCustomMessagesPerSecond() {
        String taskId = given()
            .queryParam("tiering", "30d")
            .queryParam("messagesPerSecond", "100")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .extract()
            .jsonPath()
            .getString("taskId");

        assertThat(taskId).isNotEmpty();
    }

    @Test
    void postShouldReturn400WhenMessagesPerSecondIsInvalid() {
        given()
            .queryParam("tiering", "30d")
            .queryParam("messagesPerSecond", "0")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void postShouldReturn400WhenTieringParamMissing() {
        when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("tiering"));

        verifyNoInteractions(tieringService);
    }

    @Test
    void postShouldReturn400WhenTieringParamBlank() {
        given()
            .queryParam("tiering", "")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("tiering"));

        verifyNoInteractions(tieringService);
    }

    @Test
    void postShouldReturn400WhenTieringParamInvalid() {
        given()
            .queryParam("tiering", "notaduration")
        .when()
            .post("/users/bob@example.com/data")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("message", containsString("notaduration"));

        verifyNoInteractions(tieringService);
    }
}
