package com.linagora.tmail.webadmin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

import java.util.Map;

import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.rate.limiter.api.InMemoryRateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

public class RateLimitPlanManagementRoutesTest {
    private static final String CREATE_A_PLAN_PATH = "/rate-limit-plans/%s";
    private static final String UPDATE_A_PLAN_PATH = "/rate-limit-plans/%s";
    private static final String GET_A_PLAN_PATH = "/rate-limit-plans/%s";
    private static final String GET_ALL_PLAN_PATH = "/rate-limit-plans";

    private WebAdminServer webAdminServer;
    private RateLimitingPlanRepository planRepository;

    @BeforeEach
    void setUp() {
        planRepository = new InMemoryRateLimitingPlanRepository();
        RateLimitPlanManagementRoutes routes = new RateLimitPlanManagementRoutes(planRepository, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class CreateAPlanTest {
        @Test
        void shouldSucceed() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  " +
                "}]\n" +
                "}";

            Map<String, Object> response = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(response).containsOnlyKeys("planId");
        }

        @Test
        void shouldSucceedWhenOnlyTransitLimits() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "]\n" +
                "}";

            Map<String, Object> response = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(response).containsOnlyKeys("planId");
        }

        @Test
        void shouldReturnBadRequestWhenEmptyPayLoad() {
            String json = "{}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "value should not be empty");
        }

        @Test
        void shouldReturnBadRequestWhenAllEntryAreNull() {
            String json = "{\n" +
                "  \"transitLimits\": null,\n" +
                "  \"relayLimits\": null,\n" +
                "  \"deliveryLimits\": null\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "value should not be empty");
        }

        @Test
        void shouldReturnBadRequestWhenAEntryIsEmptyArray() {
            String json = "{\"transitLimits\":[]}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "JSON payload of the request is not valid");
            assertThat(errors.get("details").toString()).contains("Operation limitation arrays must have at least one entry.");
        }

        @Test
        void shouldReturnBadRequestWhenMissingRateLimitationNameField() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(errors)
                    .containsEntry("statusCode", BAD_REQUEST_400)
                    .containsEntry("type", "InvalidArgument")
                    .containsEntry("message", "JSON payload of the request is not valid");
                softly.assertThat(errors.get("details").toString()).contains("Missing required creator property 'name'");
            });
        }

        @Test
        void shouldReturnBadRequestWhenMissingRateLimitationPeriodField() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(errors)
                    .containsEntry("statusCode", BAD_REQUEST_400)
                    .containsEntry("type", "InvalidArgument")
                    .containsEntry("message", "JSON payload of the request is not valid");
                softly.assertThat(errors.get("details").toString()).contains("Missing required creator property 'periodInSeconds'");
            });
        }

        @Test
        void shouldReturnBadRequestWhenMissingRateLimitationCountField() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(errors)
                    .containsEntry("statusCode", BAD_REQUEST_400)
                    .containsEntry("type", "InvalidArgument")
                    .containsEntry("message", "JSON payload of the request is not valid");
                softly.assertThat(errors.get("details").toString()).contains("Missing required creator property 'count'");
            });
        }

        @Test
        void shouldReturnBadRequestWhenNegativeCount() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": -100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "Predicate failed: (-100 > 0).");
        }

        @Test
        void shouldReturnBadRequestWhenMissingRateLimitationSizeField() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(errors)
                    .containsEntry("statusCode", BAD_REQUEST_400)
                    .containsEntry("type", "InvalidArgument")
                    .containsEntry("message", "JSON payload of the request is not valid");
                softly.assertThat(errors.get("details").toString()).contains("Missing required creator property 'size'");
            });
        }

        @Test
        void shouldReturnBadRequestWhenNegativeSize() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": -2048\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "Predicate failed: (-2048 > 0).");
        }

        @Test
        void shouldReturnBadRequestWhenNegativePeriod() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": -3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "requirement failed: Rate limitation period must not be negative");
        }

        @Test
        void shouldSucceedWhenValidPeriod() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

             given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON);
        }
    }

    @Nested
    class UpdateAPlanTest {
        @Test
        void shouldSucceedWhenPlanExists() {
            String createPlanJson = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            String oldPlanId = given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "oldPlanName"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("planId");

            String updatePlanJson = "{\n" +
                "  \"planName\": \"newPlanName\",\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": null\n" +
                "}";

            String response = given()
                .body(updatePlanJson)
                .put(String.format(UPDATE_A_PLAN_PATH, oldPlanId))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(response).isEmpty();
                softly.assertThat(Mono.from(planRepository.get(RateLimitingPlanId.parse(oldPlanId))).block().name())
                    .isEqualTo("newPlanName");
            });
        }

        @Test
        void shouldReturnNotFoundWhenPlanNotFound() {
            String json = "{\n" +
                "  \"planName\": \"newPlanName\",\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": null\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .put(String.format(UPDATE_A_PLAN_PATH, "fbeb01f9-2f88-4c0d-8542-0cf576d5081d"))
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "Plan does not exist");
        }

        @Test
        void shouldReturnBadRequestWhenMissingPlanName() {
            String json = "{\n" +
                "  \"transitLimits\": null" +
                ",\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": null\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .put(String.format(UPDATE_A_PLAN_PATH, "fbeb01f9-2f88-4c0d-8542-0cf576d5081d"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "JSON payload of the request is not valid");
        }

        @Test
        void shouldReturnBadRequestWhenPlanNameIsEmpty() {
            String json = "{\n" +
                "  \"planName\": \"\",\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"relayLimits\": null,\n" +
                "\"deliveryLimits\": null\n" +
                "}";

            Map<String, Object> errors = given()
                .body(json)
                .put(String.format(UPDATE_A_PLAN_PATH, "fbeb01f9-2f88-4c0d-8542-0cf576d5081d"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "Rate limiting plan name should not be empty");
        }
    }

    @Nested
    class GetAPlanTest {
        @Test
        void shouldSucceedWhenPlanExists() {
            String createPlanJson = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  " +
                "}]\n" +
                "}";

            String oldPlanId = given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "oldPlanName"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("planId");

            String response = given()
                .get(String.format(GET_A_PLAN_PATH, oldPlanId))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .whenIgnoringPaths("planId")
                .isEqualTo("{\n" +
                    "  \"planId\": \"18828c8d-35c3-4ac8-bfac-1d3c0588b40c\",\n" +
                    "  \"planName\": \"oldPlanName\",\n" +
                    "  \"transitLimits\": [{\n" +
                    "    \"name\": \"receivedMailsPerHour\",\n" +
                    "    \"periodInSeconds\": 3600,\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  },\n" +
                    "    {\n" +
                    "      \"name\": \"receivedMailsPerDay\",\n" +
                    "      \"periodInSeconds\": 86400,\n" +
                    "      \"count\": 1000,\n" +
                    "      \"size\": 4096\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"relayLimits\": [{\n" +
                    "    \"name\": \"relayMailsPerHour\",\n" +
                    "    \"periodInSeconds\": 3600,\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }],\n" +
                    "  \"deliveryLimits\": [{\n" +
                    "    \"name\": \"deliveryMailsPerHour\",\n" +
                    "    \"periodInSeconds\": 3600,\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }]\n" +
                    "}");
        }

        @Test
        void shouldReturnNotFoundWhenPlanNotFound() {
            Map<String, Object> errors = given()
                .get(String.format(GET_A_PLAN_PATH, "fbeb01f9-2f88-4c0d-8542-0cf576d5081d"))
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "Plan does not exist");
        }

        @Test
        void shouldReturnBadRequestWhenInvalidPlanId() {
            Map<String, Object> errors = given()
                .get(String.format(GET_A_PLAN_PATH, "invalid planId"))
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", "Invalid UUID string: invalid planId");
        }
    }

    @Nested
    class GetAllPlanTest {
        @Test
        void shouldReturnPlansWhenPlansExist() {
            String createPlanJson = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"periodInSeconds\": 86400,\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"periodInSeconds\": 3600,\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  " +
                "}]\n" +
                "}";

            given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "plan1"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON);

            given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "plan2"))
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON);

            String response = given()
                .get(GET_ALL_PLAN_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .whenIgnoringPaths("[0].planId", "[0].planName", "[1].planId", "[1].planName")
                .isEqualTo("[{\n" +
                    "  \"planId\": \"02d64e08-488c-4094-87c8-a511b242e802\",\n" +
                    "  \"planName\": \"plan2\",\n" +
                    "  \"transitLimits\": [{\n" +
                    "    \"name\": \"receivedMailsPerHour\",\n" +
                    "    \"periodInSeconds\": 3600,\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  },\n" +
                    "    {\n" +
                    "      \"name\": \"receivedMailsPerDay\",\n" +
                    "      \"periodInSeconds\": 86400,\n" +
                    "      \"count\": 1000,\n" +
                    "      \"size\": 4096\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"relayLimits\": [{\n" +
                    "    \"name\": \"relayMailsPerHour\",\n" +
                    "    \"periodInSeconds\": 3600,\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }],\n" +
                    "  \"deliveryLimits\": [{\n" +
                    "    \"name\": \"deliveryMailsPerHour\",\n" +
                    "    \"periodInSeconds\": 3600,\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }]\n" +
                    "},\n" +
                    "  {\n" +
                    "    \"planId\": \"5bb06b65-1b54-4ff4-8bad-ec5f425d9db6\",\n" +
                    "    \"planName\": \"plan1\",\n" +
                    "    \"transitLimits\": [{\n" +
                    "      \"name\": \"receivedMailsPerHour\",\n" +
                    "      \"periodInSeconds\": 3600,\n" +
                    "      \"count\": 100,\n" +
                    "      \"size\": 2048\n" +
                    "    },\n" +
                    "      {\n" +
                    "        \"name\": \"receivedMailsPerDay\",\n" +
                    "        \"periodInSeconds\": 86400,\n" +
                    "        \"count\": 1000,\n" +
                    "        \"size\": 4096\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"relayLimits\": [{\n" +
                    "      \"name\": \"relayMailsPerHour\",\n" +
                    "      \"periodInSeconds\": 3600,\n" +
                    "      \"count\": 100,\n" +
                    "      \"size\": 2048\n" +
                    "    }],\n" +
                    "    \"deliveryLimits\": [{\n" +
                    "      \"name\": \"deliveryMailsPerHour\",\n" +
                    "      \"periodInSeconds\": 3600,\n" +
                    "      \"count\": 100,\n" +
                    "      \"size\": 2048\n" +
                    "    }]\n" +
                    "  }\n" +
                    "]");
        }

        @Test
        void shouldReturnEmptyByDefault() {
            String response = given()
                .get(GET_ALL_PLAN_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response).isArray().isEmpty();
        }
    }
}
