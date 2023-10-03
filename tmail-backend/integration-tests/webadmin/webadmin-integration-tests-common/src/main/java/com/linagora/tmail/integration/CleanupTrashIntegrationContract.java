package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class CleanupTrashIntegrationContract {

    private static final String BASE_PATH = Constants.SEPARATOR + "mailboxes";

    @BeforeEach
    void setUp(GuiceJamesServer server) {
        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath(BASE_PATH)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void cleanupTrashShouldBeExposed() {
        given()
            .queryParam("task", "CleanupTrash")
            .post()
            .then()
            .statusCode(201)
            .body("taskId", notNullValue());
    }

    @Test
    void cleanupTrashTaskShouldWork() {
        String taskId = given()
            .queryParam("task", "CleanupTrash")
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
            .body("type", is("cleanup-trash"));
    }

    @Test
    void cleanupTrashTaskShouldFailWhenQueryParaValueIsInvalid() {
        given()
            .queryParam("task", "CleanupTrash")
            .queryParam("usersPerSecond", "abc")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Illegal value supplied for query parameter 'usersPerSecond', expecting a strictly positive optional integer"));
    }
}
