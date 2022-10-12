package com.linagora.tmail.integration;

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
                .get("/healthcheck")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .jsonPath()
                .getList("checks.componentName", String.class);

        assertThat(listComponentNames).contains("Tasks execution");
    }

    @Test
    void tasksHealthCheckShouldReturnHealthyByDefault(GuiceJamesServer jamesServer) {
        WebAdminGuiceProbe probe = jamesServer.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(probe.getWebAdminPort()).build();

        String listComponents =
            when()
                .get("/healthcheck")
            .then()
                .statusCode(HttpStatus.OK_200)
                .extract()
                .body()
                .asString();

        assertThat(listComponents).contains("""
            {"componentName":"Tasks execution","escapedComponentName":"Tasks%20execution","status":"healthy","cause":null}""");
    }
}
