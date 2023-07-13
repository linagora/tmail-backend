package com.linagora.tmail.integration;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

import org.apache.james.GuiceJamesServer;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.WebAdminUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.restassured.RestAssured;

public abstract class JmapUploadCleanRouteIntegrationContract {
    private static final String BASE_PATH = Constants.SEPARATOR + "jmap" + Constants.SEPARATOR + "uploads";

    @BeforeEach
    void setUp(GuiceJamesServer server) {
        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setBasePath(BASE_PATH)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void shouldExposeJmapUploadCleanRoute() {
        given()
            .queryParam("scope", "expired")
            .delete()
        .then()
            .statusCode(201)
            .body("taskId", notNullValue());
    }
}
