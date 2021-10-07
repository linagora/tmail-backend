package com.linagora.tmail.webadmin;

import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_2;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_DOMAIN;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TeamMailboxManagementRoutesTest {
    private static final String BASE_PATH = "/domains/%s/team-mailboxes";
    private static Stream<Arguments> namespaceInvalidSource() {
        return Stream.of(
            Arguments.of("namespace."),
            Arguments.of(".namespace"),
            Arguments.of("."),
            Arguments.of("..")
        );
    }

    private static Stream<Arguments> domainInvalidSource() {
        return Stream.of(
            Arguments.of("Dom@in"),
            Arguments.of("@")
        );
    }
    private WebAdminServer webAdminServer;
    private TeamMailboxRepository teamMailboxRepository;

    @BeforeEach
    void setUp() throws Exception {
        InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, mailboxManager.getSessionProvider());
        TeamMailboxManagementRoutes teamMailboxManagementRoutes = new TeamMailboxManagementRoutes(teamMailboxRepository, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(teamMailboxManagementRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(String.format(BASE_PATH, TEAM_MAILBOX_DOMAIN.asString()))
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class GetTeamMailboxesByDomainTest {
        @Test
        void getTeamMailboxesByDomainShouldReturnEmptyByDefault() {
            List<String> teamMailboxes = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList(".");
            assertThat(teamMailboxes).isEmpty();
        }

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#domainInvalidSource")
        void getTeamMailboxesByDomainShouldReturnErrorWhenDomainInvalid(String domain) {
            Map<String, Object> errors = given()
                .basePath(String.format(BASE_PATH, domain))
                .get()
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
                .containsEntry("details", "Domain can not be empty nor contain `@` nor `/`");
        }

        @Test
        void getTeamMailboxesByDomainShouldReturnListEntryWhenHasSingleElement() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            String response = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();
            assertThatJson(response)
                .isEqualTo("[" +
                    "    {" +
                    "        \"name\": \"marketing\"," +
                    "        \"emailAddress\": \"marketing@linagora.com\"" +
                    "    }" +
                    "]");
        }

        @Test
        void getTeamMailboxesByDomainShouldReturnListEntryWhenHasMultipleElement() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX_2)).block();
            String response = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();
            assertThatJson(response)
                .isEqualTo("[" +
                    "    {" +
                    "        \"name\": \"marketing\"," +
                    "        \"emailAddress\": \"marketing@linagora.com\"" +
                    "    }," +
                    "    {" +
                    "        \"name\": \"sale\"," +
                    "        \"emailAddress\": \"sale@linagora.com\"" +
                    "    }" +
                    "]");
        }
    }

    @Nested
    class AddTeamMailboxTest {

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void createTeamMailboxShouldReturnErrorWhenTeamMailboxNameInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .put("/" + teamMailboxName)
            .then()
                .statusCode(BAD_REQUEST_400)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void createTeamMailboxShouldStoreAssignEntry() {
            given()
                .put("/marketing")
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(Domain.of("linagora.com"))).collectList().block())
                .containsExactlyInAnyOrder(TEAM_MAILBOX);
        }

        @Test
        void createTeamMailboxShouldReturn204StatusWhenAssignTeamMailboxExists() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .put("/marketing")
            .then()
                .statusCode(NO_CONTENT_204);
        }

    }

    @Nested
    class DeleteTeamMailboxTest {
        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void deleteTeamMailboxShouldReturnErrorWhenTeamMailboxNameInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .delete("/" + teamMailboxName)
            .then()
                .statusCode(BAD_REQUEST_400)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid arguments supplied in the user request")
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void deleteTeamMailboxShouldRemoveAssignEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .delete("/marketing")
                .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listTeamMailboxes(Domain.of("linagora.com"))).collectList().block())
                .doesNotContain(TEAM_MAILBOX);
        }

        @Test
        void deleteTeamMailboxShouldReturn204StatusWhenAssignTeamMailboxDoesNotExists() {
            given()
                .put("/marketing")
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#domainInvalidSource")
        void deleteTeamMailboxShouldReturnErrorWhenDomainInvalid(String domain) {
            Map<String, Object> errors = given()
                .basePath(String.format(BASE_PATH, domain))
                .delete("/marketing")
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
                .containsEntry("details", "Domain can not be empty nor contain `@` nor `/`");
        }
    }
}
