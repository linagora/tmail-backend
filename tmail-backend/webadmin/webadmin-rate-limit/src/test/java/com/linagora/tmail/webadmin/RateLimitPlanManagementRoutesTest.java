/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package com.linagora.tmail.webadmin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.rate.limiter.api.InMemoryRateLimitationPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

public class RateLimitPlanManagementRoutesTest {
    private static final String CREATE_A_PLAN_PATH = "/rate-limit-plans/%s";
    private static final String UPDATE_A_PLAN_PATH = "/rate-limit-plans/%s";
    private static final String GET_A_PLAN_PATH = "/rate-limit-plans/%s";
    private static final String GET_ALL_PLAN_PATH = "/rate-limit-plans";

    private WebAdminServer webAdminServer;
    private RateLimitationPlanRepository planRepository;

    @BeforeEach
    void setUp() {
        planRepository = new InMemoryRateLimitationPlanRepository();
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
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  " +
                "}]\n" +
                "}";

            String response = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .whenIgnoringPaths("planId")
                .isEqualTo("{\n" +
                    "  \"planId\": \"53dda8c9-6643-4e42-8845-e06267e9098d\",\n" +
                    "  \"planName\": \"planName1\",\n" +
                    "  \"transitLimits\": [{\n" +
                    "    \"name\": \"receivedMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  },\n" +
                    "    {\n" +
                    "      \"name\": \"receivedMailsPerDay\",\n" +
                    "      \"period\": \"86400 second\",\n" +
                    "      \"count\": 1000,\n" +
                    "      \"size\": 4096\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"relayLimits\": [{\n" +
                    "    \"name\": \"relayMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }],\n" +
                    "  \"deliveryLimits\": [{\n" +
                    "    \"name\": \"deliveryMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  " +
                    "}]\n" +
                    "}");
        }

        @Test
        void shouldSucceedWhenOnlyTransitLimits() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "]\n" +
                "}";

            String response = given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .whenIgnoringPaths("planId")
                .isEqualTo("{\n" +
                    "  \"planId\": \"b0cd165e-63e9-4312-a549-3e1a54075627\",\n" +
                    "  \"planName\": \"planName1\",\n" +
                    "  \"transitLimits\": [{\n" +
                    "    \"name\": \"receivedMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  },\n" +
                    "    {\n" +
                    "      \"name\": \"receivedMailsPerDay\",\n" +
                    "      \"period\": \"86400 second\",\n" +
                    "      \"count\": 1000,\n" +
                    "      \"size\": 4096\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"relayLimits\": null,\n" +
                    "  \"deliveryLimits\": null" +
                    "\n" +
                    "}");
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
            assertThat(errors.get("details").toString()).contains("Operation limitation arrays must have at least a entry.");
        }

        @Test
        void shouldReturnBadRequestWhenMissingRateLimitationNameField() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"period\": \"1 hour\",\n" +
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
                softly.assertThat(errors.get("details").toString()).contains("Missing required creator property 'period'");
            });
        }

        @Test
        void shouldReturnBadRequestWhenMissingRateLimitationCountField() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
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
        void shouldReturnBadRequestWhenInvalidPeriodString() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"period\": \"invalid period\",\n" +
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
                .containsEntry("details", "Supplied value do not follow the unit format (number optionally suffixed with a string representing the unit");
        }

        @Test
        void shouldReturnBadRequestWhenNegativePeriod() {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"period\": \"-1 hour\",\n" +
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
                .containsEntry("details", "Duration amount should be positive");
        }

        @ParameterizedTest
        @ValueSource(strings = {"2s", "2 s", "2 sec", "2 Sec", "2 second", "2 seconds",
            "2m", "2 m", "2 min", "2 mins", "2 minute", "2 Minute", "2 minutes",
            "2h", "2 h", "2 hour", "2 Hour", "2 hours",
            "2d", "2 d", "2 day", "2 Day", "2 days",
            "2w", "2 w", "2 week", "2 Week", "2 weeks",
            "2months", "2 months", "2 month", "2 Month"})
        void shouldSucceedWhenValidPeriod(String period) {
            String json = "{\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"period\": \"" + period + "\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

             given()
                .body(json)
                .post(String.format(CREATE_A_PLAN_PATH, "planName1"))
            .then()
                .statusCode(OK_200)
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
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "}]\n" +
                "}";

            String oldPlanId = given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "oldPlanName"))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getString("planId");

            String updatePlanJson = "{\n" +
                "  \"planName\": \"newPlanName\",\n" +
                "  \"transitLimits\": [{\n" +
                "    \"name\": \"receivedMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  " +
                "}]\n" +
                "}";

            String oldPlanId = given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "oldPlanName"))
            .then()
                .statusCode(OK_200)
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
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  },\n" +
                    "    {\n" +
                    "      \"name\": \"receivedMailsPerDay\",\n" +
                    "      \"period\": \"86400 second\",\n" +
                    "      \"count\": 1000,\n" +
                    "      \"size\": 4096\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"relayLimits\": [{\n" +
                    "    \"name\": \"relayMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }],\n" +
                    "  \"deliveryLimits\": [{\n" +
                    "    \"name\": \"deliveryMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
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
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  },\n" +
                "    {\n" +
                "      \"name\": \"receivedMailsPerDay\",\n" +
                "      \"period\": \"1 day\",\n" +
                "      \"count\": 1000,\n" +
                "      \"size\": 4096\n" +
                "    }\n" +
                "  ],\n" +
                "  \"relayLimits\": [{\n" +
                "    \"name\": \"relayMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  }],\n" +
                "  \"deliveryLimits\": [{\n" +
                "    \"name\": \"deliveryMailsPerHour\",\n" +
                "    \"period\": \"1 hour\",\n" +
                "    \"count\": 100,\n" +
                "    \"size\": 2048\n" +
                "  " +
                "}]\n" +
                "}";

            given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "plan1"))
            .then()
                .statusCode(OK_200)
                .contentType(JSON);

            given()
                .body(createPlanJson)
                .post(String.format(CREATE_A_PLAN_PATH, "plan2"))
            .then()
                .statusCode(OK_200)
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
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  },\n" +
                    "    {\n" +
                    "      \"name\": \"receivedMailsPerDay\",\n" +
                    "      \"period\": \"86400 second\",\n" +
                    "      \"count\": 1000,\n" +
                    "      \"size\": 4096\n" +
                    "    }\n" +
                    "  ],\n" +
                    "  \"relayLimits\": [{\n" +
                    "    \"name\": \"relayMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }],\n" +
                    "  \"deliveryLimits\": [{\n" +
                    "    \"name\": \"deliveryMailsPerHour\",\n" +
                    "    \"period\": \"3600 second\",\n" +
                    "    \"count\": 100,\n" +
                    "    \"size\": 2048\n" +
                    "  }]\n" +
                    "},\n" +
                    "  {\n" +
                    "    \"planId\": \"5bb06b65-1b54-4ff4-8bad-ec5f425d9db6\",\n" +
                    "    \"planName\": \"plan1\",\n" +
                    "    \"transitLimits\": [{\n" +
                    "      \"name\": \"receivedMailsPerHour\",\n" +
                    "      \"period\": \"3600 second\",\n" +
                    "      \"count\": 100,\n" +
                    "      \"size\": 2048\n" +
                    "    },\n" +
                    "      {\n" +
                    "        \"name\": \"receivedMailsPerDay\",\n" +
                    "        \"period\": \"86400 second\",\n" +
                    "        \"count\": 1000,\n" +
                    "        \"size\": 4096\n" +
                    "      }\n" +
                    "    ],\n" +
                    "    \"relayLimits\": [{\n" +
                    "      \"name\": \"relayMailsPerHour\",\n" +
                    "      \"period\": \"3600 second\",\n" +
                    "      \"count\": 100,\n" +
                    "      \"size\": 2048\n" +
                    "    }],\n" +
                    "    \"deliveryLimits\": [{\n" +
                    "      \"name\": \"deliveryMailsPerHour\",\n" +
                    "      \"period\": \"3600 second\",\n" +
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
