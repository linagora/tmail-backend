package com.linagora.tmail.webadmin;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.stream.Stream;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.linagora.tmail.rate.limiter.api.InMemoryRateLimitationPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitationPlanRepositoryContract;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanNotFoundException;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository;
import com.linagora.tmail.rate.limiter.api.memory.MemoryRateLimitingPlanUserRepository;

import io.restassured.RestAssured;
import io.restassured.path.json.JsonPath;
import reactor.core.publisher.Mono;

public class RateLimitPlanUsersRoutesTest {
    private static final String ATTACH_PLAN_TO_USER_PATH = "/users/%s/rate-limit-plans/%s";
    private static final String GET_USERS_OF_PLAN_PATH = "/rate-limit-plans/%s/users";
    private static final String GET_PLAN_OF_USER_PATH = "/users/%s/rate-limit-plans";
    private static final String REVOKE_PLAN_OF_USER_PATH = "/users/%s/rate-limit-plans";
    private static final String VALID_PLAN_ID = "f5f3ce18-504a-4f32-a6f6-3ae41b096dbd";
    public static Username BOB = Username.of("bob@linagora.com");
    public static Username ANDRE = Username.of("andre@linagora.com");

    private static Stream<Arguments> usernameInvalidSource() {
        return Stream.of(
            Arguments.of("@"),
            Arguments.of("aa@aa@aa")
        );
    }

    private WebAdminServer webAdminServer;
    private UsersRepository usersRepository;
    private RateLimitingPlanUserRepository planUserRepository;
    private RateLimitationPlanRepository planRepository;

    @BeforeEach
    void setUp() {
        planRepository = new InMemoryRateLimitationPlanRepository();
        planUserRepository = new MemoryRateLimitingPlanUserRepository();
        usersRepository = mock(UsersRepository.class);
        RateLimitPlanUserRoutes rateLimitPlanUserRoutes = new RateLimitPlanUserRoutes(planUserRepository, planRepository, usersRepository, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(rateLimitPlanUserRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class AttachPlanToUserTest {

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.RateLimitPlanUsersRoutesTest#usernameInvalidSource")
        void shouldReturnErrorWhenInvalidUser(String username) {
            Map<String, Object> errors = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, username, VALID_PLAN_ID))
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
                .containsEntry("message", "Invalid arguments supplied in the user request");
        }

        @Test
        void shouldReturnNotFoundWhenUserNotFound() {
            Map<String, Object> errors = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), VALID_PLAN_ID))
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
                .containsEntry("message", "User " + BOB.asString() + " does not exist");
        }

        @Test
        void shouldReturnErrorWhenInvalidPlanId() {
            Map<String, Object> errors = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), "invalidUUIDString"))
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
                .containsEntry("details", "Invalid UUID string: invalidUUIDString");
        }

        @Test
        void shouldReturnNotFoundWhenPlanDoesNotExist() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);

            Map<String, Object> errors = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), VALID_PLAN_ID))
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
                .containsEntry("message", "Plan id " + VALID_PLAN_ID + " does not exist");
        }


        @Test
        void shouldSucceedWhenPlanExists() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();

            String response = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), planId.serialize()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(response).isEmpty();
                softly.assertThat(Mono.from(planUserRepository.getPlanByUser(BOB)).block()).isEqualTo(planId);
            });
        }

        @Test
        void shouldOverridePreviousPlanWithNewPlan() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId previousPlanId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();
            RateLimitingPlanId newPlanId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();

            given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), previousPlanId.serialize()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            String response = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), newPlanId.serialize()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(response).isEmpty();
                softly.assertThat(Mono.from(planUserRepository.getPlanByUser(BOB)).block()).isEqualTo(newPlanId);
            });
        }

        @Test
        void shouldBeIdempotent() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();

            given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), planId.serialize()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            String response = given()
                .put(String.format(ATTACH_PLAN_TO_USER_PATH, BOB.asString(), planId.serialize()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(response).isEmpty();
                softly.assertThat(Mono.from(planUserRepository.getPlanByUser(BOB)).block()).isEqualTo(planId);
            });
        }
    }

    @Nested
    class GetUsersOfPlan {

        @Test
        void shouldReturnErrorWhenInvalidPlanId() {
            Map<String, Object> errors = given()
                .get(String.format(GET_USERS_OF_PLAN_PATH, "invalidUUIDString"))
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
                .containsEntry("details", "Invalid UUID string: invalidUUIDString");
        }

        @Test
        void shouldReturnNotFoundWhenPlanDoesNotExist() {
            Map<String, Object> errors = given()
                .get(String.format(GET_USERS_OF_PLAN_PATH, VALID_PLAN_ID))
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
                .containsEntry("message", "Plan id " + VALID_PLAN_ID + " does not exist");
        }


        @Test
        void shouldReturnUsersWhenPlanExistsAndHasUsersAttached() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();
            Mono.from(planUserRepository.applyPlan(BOB, planId)).block();
            Mono.from(planUserRepository.applyPlan(ANDRE, planId)).block();

            String response = given()
                .get(String.format(GET_USERS_OF_PLAN_PATH, planId.serialize()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo("[\"bob@linagora.com\", \"andre@linagora.com\"]");
        }

        @Test
        void shouldReturnEmptyWhenPlanExistsAndHasNonUsersAttached() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();

            String response = given()
                .get(String.format(GET_USERS_OF_PLAN_PATH, planId.serialize()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .isEqualTo("[]");
        }
    }

    @Nested
    class GetPlanOfUserTest {

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.RateLimitPlanUsersRoutesTest#usernameInvalidSource")
        void shouldReturnErrorWhenInvalidUser(String username) {
            Map<String, Object> errors = given()
                .get(String.format(GET_PLAN_OF_USER_PATH, username))
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
                .containsEntry("message", "Invalid arguments supplied in the user request");
        }

        @Test
        void shouldReturnNotFoundWhenUserNotFound() {
            Map<String, Object> errors = given()
                .get(String.format(GET_PLAN_OF_USER_PATH, BOB.asString()))
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
                .containsEntry("message", "User " + BOB.asString() + " does not exist");
        }

        @Test
        void shouldReturnNotFoundWhenUserDoesNotHaveAPlan() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);

            Map<String, Object> errors = given()
                .get(String.format(GET_PLAN_OF_USER_PATH, BOB.asString()))
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
                .containsEntry("message", "User " + BOB.asString() + " does not have a plan");
        }

        @Test
        void shouldReturnPlanIdWhenUserHasAPlan() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();
            Mono.from(planUserRepository.applyPlan(BOB, planId)).block();

            JsonPath jsonPath = given()
                .get(String.format(GET_PLAN_OF_USER_PATH, BOB.asString()))
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath();

            assertThat(jsonPath.getString("planId")).isEqualTo(planId.serialize());
        }
    }

    @Nested
    class RevokePlanOfUser {

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.RateLimitPlanUsersRoutesTest#usernameInvalidSource")
        void shouldReturnErrorWhenInvalidUser(String username) {
            Map<String, Object> errors = given()
                .delete(String.format(REVOKE_PLAN_OF_USER_PATH, username))
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
                .containsEntry("message", "Invalid arguments supplied in the user request");
        }

        @Test
        void shouldReturnNotFoundWhenUserNotFound() {
            Map<String, Object> errors = given()
                .delete(String.format(REVOKE_PLAN_OF_USER_PATH, BOB.asString()))
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
                .containsEntry("message", "User " + BOB.asString() + " does not exist");
        }

        @Test
        void shouldRevokePlanOfUser() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();
            Mono.from(planUserRepository.applyPlan(BOB, planId)).block();

            String response = given()
                .delete(String.format(REVOKE_PLAN_OF_USER_PATH, BOB.asString()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThat(response).isEmpty();
            assertThatThrownBy(() -> Mono.from(planUserRepository.getPlanByUser(BOB)).block())
                .isInstanceOf(RateLimitingPlanNotFoundException.class);
        }

        @Test
        void shouldBeIdempotent() throws UsersRepositoryException {
            when(usersRepository.contains(BOB)).thenReturn(true);
            RateLimitingPlanId planId = Mono.from(planRepository.create(RateLimitationPlanRepositoryContract.CREATION_REQUEST())).block().id();
            Mono.from(planUserRepository.applyPlan(BOB, planId)).block();

            given()
                .delete(String.format(REVOKE_PLAN_OF_USER_PATH, BOB.asString()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            String response = given()
                .delete(String.format(REVOKE_PLAN_OF_USER_PATH, BOB.asString()))
            .then()
                .statusCode(NO_CONTENT_204)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(response).isEmpty();
                softly.assertThatThrownBy(() -> Mono.from(planUserRepository.getPlanByUser(BOB)).block())
                    .isInstanceOf(RateLimitingPlanNotFoundException.class);
            });
        }
    }
}
