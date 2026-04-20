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
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.apache.james.core.Domain;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.json.DTOConverter;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.Task;
import org.apache.james.task.TaskExecutionDetails;
import org.apache.james.task.TaskId;
import org.apache.james.task.TaskManager;
import org.apache.james.task.TaskType;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

class DomainTasksRoutesTest {
    private static final Domain DOMAIN = Domain.of("linagora.com");
    private static final Domain OTHER_DOMAIN = Domain.of("other.com");
    private static final TaskType TEST_TASK_TYPE = TaskType.of("domain-bound-test-task");

    record DomainBoundAdditionalInformation(Domain domain) implements TaskExecutionDetails.AdditionalInformation {
        @Override
        public Instant timestamp() {
            return Instant.now();
        }
    }

    static class DomainBoundTask implements Task {
        private final Domain domain;

        DomainBoundTask(Domain domain) {
            this.domain = domain;
        }

        @Override
        public Result run() {
            return Result.COMPLETED;
        }

        @Override
        public TaskType type() {
            return TEST_TASK_TYPE;
        }

        @Override
        public Optional<TaskExecutionDetails.AdditionalInformation> details() {
            return Optional.of(new DomainBoundAdditionalInformation(domain));
        }
    }

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;

    @BeforeEach
    void setUp() throws Exception {
        taskManager = new MemoryTaskManager(new Hostname("localhost"));

        MemoryDomainList memoryDomainList = new MemoryDomainList(null);
        memoryDomainList.configure(DomainListConfiguration.DEFAULT);
        memoryDomainList.addDomain(DOMAIN);
        DomainList domainList = memoryDomainList;

        JsonTransformer jsonTransformer = new JsonTransformer();
        DTOConverter additionalInformationConverter = DTOConverter.of();

        Set<TaskBelongsToDomainPredicate> predicates = Set.of(
            (domain, details) -> details.getAdditionalInformation()
                .filter(info -> info instanceof DomainBoundAdditionalInformation)
                .map(info -> ((DomainBoundAdditionalInformation) info).domain().equals(domain))
                .orElse(false));

        DomainTasksRoutes domainTasksRoutes = new DomainTasksRoutes(
            taskManager,
            domainList,
            predicates,
            additionalInformationConverter,
            jsonTransformer);

        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer, additionalInformationConverter);

        webAdminServer = WebAdminUtils.createWebAdminServer(domainTasksRoutes, tasksRoutes).start();
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    private TaskId submitAndAwait(Domain domain) throws Exception {
        TaskId taskId = taskManager.submit(new DomainBoundTask(domain));
        taskManager.await(taskId, java.time.Duration.ofSeconds(5));
        return taskId;
    }

    private TaskId submitRunning(Domain domain) {
        return taskManager.submit(new DomainBoundTask(domain));
    }

    @Test
    void getTaskShouldReturnTaskDetailsWhenTaskBelongsToDomain() throws Exception {
        TaskId taskId = submitAndAwait(DOMAIN);

        String response = when()
            .get("/domains/" + DOMAIN.asString() + "/tasks/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.OK_200)
            .extract().body().asString();

        assertThatJson(response)
            .isEqualTo("{" +
                "  \"taskId\": \"" + taskId.getValue() + "\"," +
                "  \"status\": \"completed\"," +
                "  \"type\": \"${json-unit.ignore}\"," +
                "  \"startedDate\": \"${json-unit.ignore}\"," +
                "  \"completedDate\": \"${json-unit.ignore}\"," +
                "  \"submitDate\": \"${json-unit.ignore}\"," +
                "  \"submittedFrom\": \"${json-unit.ignore}\"," +
                "  \"executedOn\": \"${json-unit.ignore}\"," +
                "  \"additionalInformation\": \"${json-unit.ignore}\"," +
                "  \"cancelledFrom\": \"${json-unit.ignore}\"" +
                "}");
    }

    @Test
    void getTaskShouldReturn404WhenTaskBelongsToOtherDomain() throws Exception {
        TaskId taskId = submitAndAwait(OTHER_DOMAIN);

        when()
            .get("/domains/" + DOMAIN.asString() + "/tasks/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getTaskShouldReturn404WhenDomainDoesNotExist() throws Exception {
        TaskId taskId = submitAndAwait(DOMAIN);

        when()
            .get("/domains/unknown.com/tasks/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getTaskShouldReturn404WhenTaskIdNotFound() {
        when()
            .get("/domains/" + DOMAIN.asString() + "/tasks/" + TaskId.generateTaskId().getValue())
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void getTaskShouldReturn400WhenTaskIdInvalid() {
        when()
            .get("/domains/" + DOMAIN.asString() + "/tasks/invalid-uuid")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400);
    }

    @Test
    void awaitTaskShouldReturnCompletedStatusWhenTaskBelongsToDomain() {
        TaskId taskId = submitRunning(DOMAIN);

        when()
            .get("/domains/" + DOMAIN.asString() + "/tasks/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.OK_200);
    }

    @Test
    void awaitTaskShouldReturn404WhenTaskBelongsToOtherDomain() throws Exception {
        TaskId taskId = submitAndAwait(OTHER_DOMAIN);

        when()
            .get("/domains/" + DOMAIN.asString() + "/tasks/" + taskId.getValue() + "/await")
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }

    @Test
    void cancelTaskShouldReturn204WhenTaskBelongsToDomain() throws Exception {
        TaskId taskId = submitAndAwait(DOMAIN);

        given()
            .delete("/domains/" + DOMAIN.asString() + "/tasks/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.NO_CONTENT_204);

        assertThat(taskManager.getExecutionDetails(taskId).getStatus())
            .isIn(TaskManager.Status.CANCELLED, TaskManager.Status.COMPLETED);
    }

    @Test
    void cancelTaskShouldReturn404WhenTaskBelongsToOtherDomain() throws Exception {
        TaskId taskId = submitAndAwait(OTHER_DOMAIN);

        given()
            .delete("/domains/" + DOMAIN.asString() + "/tasks/" + taskId.getValue())
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404);
    }
}
