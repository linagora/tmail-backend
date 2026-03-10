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

package com.linagora.tmail.webadmin.label;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.CREATED_201;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.label.LabelRepository;
import com.linagora.tmail.james.jmap.label.MemoryLabelRepository;
import com.linagora.tmail.james.jmap.model.Color;
import com.linagora.tmail.james.jmap.model.DisplayName;
import com.linagora.tmail.james.jmap.model.Label;
import com.linagora.tmail.james.jmap.model.LabelCreationRequest;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;
import scala.Option;
import scala.Option$;
import scala.compat.java8.OptionConverters;

class LabelRoutesTest {
    private static final Username BOB = Username.of("bob@linagora.com");
    private static final String BOB_LABELS_PATH = "/users/bob@linagora.com/labels";

    private WebAdminServer webAdminServer;
    private LabelRepository labelRepository;
    private UsersRepository usersRepository;

    @BeforeEach
    void setUp() throws Exception {
        labelRepository = new MemoryLabelRepository();
        usersRepository = mock(UsersRepository.class);
        when(usersRepository.contains(BOB)).thenReturn(true);

        LabelRoutes routes = new LabelRoutes(labelRepository, usersRepository, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(routes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer).build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @SuppressWarnings("unchecked")
    private static Option<Color> noColor() {
        return OptionConverters.toScala(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private static Option<String> noDescription() {
        return OptionConverters.toScala(Optional.empty());
    }

    @Nested
    class GetLabels {
        @Test
        void getLabelsReturnsEmptyListWhenNone() {
            when()
                .get(BOB_LABELS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .body(".", hasSize(0));
        }

        @Test
        void getLabelsReturnsExistingLabels() {
            LabelCreationRequest req1 = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            LabelCreationRequest req2 = new LabelCreationRequest(
                new DisplayName("Personal"), noColor(), noDescription());
            Mono.from(labelRepository.addLabel(BOB, req1)).block();
            Mono.from(labelRepository.addLabel(BOB, req2)).block();

            String response = when()
                .get(BOB_LABELS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .body(".", hasSize(2))
                .extract().body().asString();

            assertThatJson(response).isArray()
                .anySatisfy(node -> assertThatJson(node).node("displayName").isEqualTo("\"Work\""))
                .anySatisfy(node -> assertThatJson(node).node("displayName").isEqualTo("\"Personal\""));
        }

        @Test
        void getLabelsReturnsAllFieldsWhenSet() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Urgent"),
                Option$.MODULE$.apply(Color.validate("#ff0000").toOption().get()),
                Option$.MODULE$.apply("Very important emails"));
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            String response = when()
                .get(BOB_LABELS_PATH)
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .body(".", hasSize(1))
                .extract().body().asString();

            assertThatJson(response).isArray().first()
                .satisfies(node -> {
                    assertThatJson(node).node("id").isNotNull();
                    assertThatJson(node).node("displayName").isEqualTo("\"Urgent\"");
                    assertThatJson(node).node("keyword").isNotNull();
                    assertThatJson(node).node("color").isEqualTo("\"#ff0000\"");
                    assertThatJson(node).node("description").isEqualTo("\"Very important emails\"");
                });
        }

        @Test
        void getLabelsReturns404WhenUserDoesNotExist() throws Exception {
            when(usersRepository.contains(Username.of("unknown@linagora.com"))).thenReturn(false);

            when()
                .get("/users/unknown@linagora.com/labels")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }

    @Nested
    class CreateLabel {
        @Test
        void createLabelReturns201WithCreatedLabel() {
            String response = given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response)
                .node("id").isNotNull();
            assertThatJson(response)
                .node("displayName").isEqualTo("\"Work\"");
            assertThatJson(response)
                .node("keyword").isNotNull();
        }

        @Test
        void createLabelWithAllFields() {
            String response = given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"color\": \"#ff0000\", \"description\": \"work emails\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).node("displayName").isEqualTo("\"Work\"");
            assertThatJson(response).node("color").isEqualTo("\"#ff0000\"");
            assertThatJson(response).node("description").isEqualTo("\"work emails\"");
        }

        @Test
        void createLabelReturns400WhenDisplayNameMissing() {
            given()
                .contentType(JSON)
                .body("{\"color\": \"#ff0000\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void createLabelReturns400WhenDisplayNameEmpty() {
            given()
                .contentType(JSON)
                .body("{\"displayName\": \"\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void createLabelReturns400WhenColorInvalid() {
            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"color\": \"notacolor\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void createLabelReturns404WhenUserDoesNotExist() throws Exception {
            when(usersRepository.contains(Username.of("unknown@linagora.com"))).thenReturn(false);

            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\"}")
            .when()
                .post("/users/unknown@linagora.com/labels")
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void createLabelWithExplicitKeyword() {
            String response = given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"keyword\": \"label42\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).node("keyword").isEqualTo("\"label42\"");
            assertThatJson(response).node("id").isEqualTo("\"label42\"");
        }

        @Test
        void createLabelReturns400WhenKeywordStartsWithDollar() {
            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"keyword\": \"$label42\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void createLabelWithoutKeywordGeneratesRandom() {
            String response = given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(CREATED_201)
                .contentType(JSON)
                .extract().body().asString();

            assertThatJson(response).node("keyword").isNotNull();
        }

        @Test
        void createLabelReturns400WhenKeywordInvalid() {
            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"keyword\": \"invalid keyword!\"}")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void createLabelReturns400OnEmptyBody() {
            given()
                .contentType(JSON)
                .body("")
            .when()
                .post(BOB_LABELS_PATH)
            .then()
                .statusCode(BAD_REQUEST_400);
        }
    }

    @Nested
    class UpdateLabel {
        @Test
        void updateLabelReturns204OnSuccess() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Job\"}")
            .when()
                .patch(BOB_LABELS_PATH + "/" + created.id().serialize())
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void updateLabelPersistsDisplayNameChange() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Job\"}")
            .when()
                .patch(BOB_LABELS_PATH + "/" + created.id().serialize());

            String response = when()
                .get(BOB_LABELS_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(response).isArray().first()
                .satisfies(node -> assertThatJson(node).node("displayName").isEqualTo("\"Job\""));
        }

        @Test
        void updateLabelCanSetColor() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"color\": \"#abcdef\"}")
            .when()
                .patch(BOB_LABELS_PATH + "/" + created.id().serialize());

            String response = when()
                .get(BOB_LABELS_PATH)
            .then()
                .statusCode(OK_200)
                .extract().body().asString();

            assertThatJson(response).isArray().first()
                .satisfies(node -> assertThatJson(node).node("color").isEqualTo("\"#abcdef\""));
        }

        @Test
        void updateLabelReturns400WhenDisplayNameNull() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            given()
                .contentType(JSON)
                .body("{\"displayName\": null}")
            .when()
                .patch(BOB_LABELS_PATH + "/" + created.id().serialize())
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void updateLabelReturns400WhenColorInvalid() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Work\", \"color\": \"notacolor\"}")
            .when()
                .patch(BOB_LABELS_PATH + "/" + created.id().serialize())
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void updateLabelReturns404WhenLabelDoesNotExist() {
            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Job\"}")
            .when()
                .patch(BOB_LABELS_PATH + "/nonexistent-label-id")
            .then()
                .statusCode(NOT_FOUND_404);
        }

        @Test
        void updateLabelReturns404WhenUserDoesNotExist() throws Exception {
            when(usersRepository.contains(Username.of("unknown@linagora.com"))).thenReturn(false);

            given()
                .contentType(JSON)
                .body("{\"displayName\": \"Job\"}")
            .when()
                .patch("/users/unknown@linagora.com/labels/someid")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }

    @Nested
    class DeleteLabel {
        @Test
        void deleteLabelReturns204OnSuccess() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            when()
                .delete(BOB_LABELS_PATH + "/" + created.id().serialize())
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteLabelActuallyRemovesIt() {
            LabelCreationRequest req = new LabelCreationRequest(
                new DisplayName("Work"), noColor(), noDescription());
            Label created = Mono.from(labelRepository.addLabel(BOB, req)).block();

            when().delete(BOB_LABELS_PATH + "/" + created.id().serialize());

            when()
                .get(BOB_LABELS_PATH)
            .then()
                .statusCode(OK_200)
                .body(".", hasSize(0));
        }

        @Test
        void deleteLabelReturns204WhenLabelDoesNotExist() {
            when()
                .delete(BOB_LABELS_PATH + "/nonexistentlabelid")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteLabelReturns404WhenUserDoesNotExist() throws Exception {
            when(usersRepository.contains(Username.of("unknown@linagora.com"))).thenReturn(false);

            when()
                .delete("/users/unknown@linagora.com/labels/someid")
            .then()
                .statusCode(NOT_FOUND_404);
        }
    }
}
