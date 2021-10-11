package com.linagora.tmail.webadmin;

import static com.linagora.tmail.webadmin.TeamMailboxFixture.ANDRE;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.BOB;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_2;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_DOMAIN;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.NO_CONTENT_204;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.rrt.api.MappingConflictException;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.lib.MappingSource;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.api.AlreadyExistInUsersRepositoryException;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.linagora.tmail.team.TeamMailboxUserEntityValidator;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import net.javacrumbs.jsonunit.core.Option;
import net.javacrumbs.jsonunit.core.internal.Options;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TeamMailboxManagementRoutesTest {
    private static final String BASE_PATH = "/domains/%s/team-mailboxes";
    private static final String TEAM_MEMBER_BASE_PATH = BASE_PATH + "/%s/members";
    private static Stream<Arguments> namespaceInvalidSource() {
        return Stream.of(
            Arguments.of("namespace."),
            Arguments.of(".namespace"),
            Arguments.of("."),
            Arguments.of(".."),
            Arguments.of("marketing.team")
        );
    }

    private static Stream<Arguments> domainInvalidSource() {
        return Stream.of(
            Arguments.of("Dom@in"),
            Arguments.of("@")
        );
    }
    private static Stream<Arguments> usernameInvalidSource() {
        return Stream.of(
            Arguments.of("@"),
            Arguments.of("aa@aa@aa")
        );
    }

    private WebAdminServer webAdminServer;
    private TeamMailboxRepositoryImpl teamMailboxRepository;
    private MemoryUsersRepository usersRepository;
    private MemoryRecipientRewriteTable recipientRewriteTable;

    @BeforeEach
    void setUp() throws Exception {
        recipientRewriteTable = new MemoryRecipientRewriteTable();
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(TEAM_MAILBOX_DOMAIN);
        recipientRewriteTable.setDomainList(domainList);
        recipientRewriteTable.setConfiguration(RecipientRewriteTableConfiguration.DEFAULT_ENABLED);
        usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);

        InMemoryMailboxManager mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, mailboxManager.getSessionProvider());

        UserEntityValidator validator = UserEntityValidator.aggregate(
            new DefaultUserEntityValidator(usersRepository),
            new RecipientRewriteTableUserEntityValidator(recipientRewriteTable),
            new TeamMailboxUserEntityValidator(teamMailboxRepository));

        usersRepository.setValidator(validator);
        recipientRewriteTable.setUsersRepository(usersRepository);
        recipientRewriteTable.setUserEntityValidator(validator);
        teamMailboxRepository.setValidator(validator);

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
    class TeamMailboxUserEntityValidatorTest {
        @Test
        void createTeamMailboxShouldNotConflictWithUser() throws UsersRepositoryException {
            usersRepository.addUser(BOB, "whatever");

            Map<String, Object> errors = when()
                .put("/bob")
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.CONFLICT_409)
                .containsEntry("type", "WrongState")
                .containsEntry("message", "'bob@linagora.com' user already exist");
        }

        @Test
        void createTeamMailboxShouldNotConflictWithGroup() throws Exception {
            recipientRewriteTable.addGroupMapping(MappingSource.fromUser(BOB), ANDRE.asString());

            Map<String, Object> errors = when()
                .put("/bob")
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.CONFLICT_409)
                .containsEntry("type", "WrongState")
                .containsEntry("message", "'bob@linagora.com' already have associated mappings: group:andre@linagora.com");
        }

        @Test
        void createTeamMailboxShouldNotConflictWithAlias() throws Exception {
            recipientRewriteTable.addAliasMapping(MappingSource.fromUser(BOB), ANDRE.asString());

            Map<String, Object> errors = when()
                .put("/bob")
            .then()
                .statusCode(HttpStatus.CONFLICT_409)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.CONFLICT_409)
                .containsEntry("type", "WrongState")
                .containsEntry("message", "'bob@linagora.com' already have associated mappings: alias:andre@linagora.com");
        }

        @Test
        void createUserShouldNotConflictWithTeamMailbox() {
            when()
                .put("/bob")
            .then()
                .statusCode(NO_CONTENT_204);

            assertThatThrownBy(() -> usersRepository.addUser(BOB, "whatever"))
                .isInstanceOf(AlreadyExistInUsersRepositoryException.class)
                .hasMessage("'bob@linagora.com' team-mailbox already exists");
        }

        @Test
        void createGroupShouldNotConflictWithTeamMailbox() {
            when()
                .put("/bob")
            .then()
                .statusCode(NO_CONTENT_204);

            assertThatThrownBy(() -> recipientRewriteTable.addGroupMapping(MappingSource.fromUser(BOB), ANDRE.asString()))
                .isInstanceOf(MappingConflictException.class)
                .hasMessage("'bob@linagora.com' team-mailbox already exists");
        }

        @Test
        void createAliasShouldNotConflictWithTeamMailbox() {
            when()
                .put("/bob")
                .then()
                .statusCode(NO_CONTENT_204);

            assertThatThrownBy(() -> recipientRewriteTable.addAliasMapping(MappingSource.fromUser(BOB), ANDRE.asString()))
                .isInstanceOf(MappingConflictException.class)
                .hasMessage("'bob@linagora.com' team-mailbox already exists");
        }

        @Test
        void teamMailboxUserEntityValidatorShouldNotThrowWhenNameWithDot() {
            assertThatCode(() -> usersRepository.addUser(Username.of("bob.by@linagora.com"), "whatever"))
                .doesNotThrowAnyException();
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

    @Nested
    class GetTeamMailboxMembersTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MEMBER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString()))
                .build();
        }

        @Test
        void getTeamMailboxMembersShouldReturnErrorWhenTeamMailboxDoesNotExists() {
            Map<String, Object> errors = given()
                .get()
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
                .containsEntry("message", "The requested team mailbox does not exists")
                .containsEntry("details", TEAM_MAILBOX.mailboxPath().asString() + " can not be found");
        }

        @Test
        void getTeamMailboxMembersShouldReturnEmptyByDefault() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            List<String> members = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .jsonPath()
                .getList(".");

            assertThat(members).isEmpty();
        }

        @Test
        void getTeamMailboxMembersShouldReturnListEntryWhenHasSingleElement() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, BOB)).block();
            String response  = given()
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
                    "        \"username\": \"bob@linagora.com\"" +
                    "    }" +
                    "]");
        }

        @Test
        void getTeamMailboxMembersShouldReturnListEntryWhenHasMultipleElement() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, BOB)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, ANDRE)).block();
            String response  = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
                .isEqualTo("[" +
                    "    {" +
                    "        \"username\": \"bob@linagora.com\"" +
                    "    }," +
                    "    {" +
                    "        \"username\": \"andre@linagora.com\"" +
                    "    }" +
                    "]");
        }

        @Test
        void getTeamMailboxMembersShouldNotReturnEntriesOfAnotherTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX_2)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX_2, BOB)).block();
            String response  = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(new Options(Option.IGNORING_ARRAY_ORDER))
                .isEqualTo("[]");
        }
    }

    @Nested
    class AddMemberTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MEMBER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString()))
                .build();
        }

        @Test
        void addMemberShouldReturnErrorWhenTeamMailboxDoesNotExists() {
            Map<String, Object> errors = given()
                .put("/" + BOB.asString())
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
                .containsEntry("message", "The requested team mailbox does not exists")
                .containsEntry("details", TEAM_MAILBOX.mailboxPath().asString() + " can not be found");
        }

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#usernameInvalidSource")
        void addMemberShouldReturnErrorWhenInvalidUser(String username) {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Map<String, Object> errors = given()
                .put("/" + username)
            .then()
                .statusCode(BAD_REQUEST_400)
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
        void addMemberShouldStoreAssignEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .put("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .containsExactlyInAnyOrder(BOB);
        }
        @Test
        void addMemberShouldReturn204StatusWhenUserAlreadyInTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, BOB)).block();
            given()
                .put("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);
        }
    }

    @Nested
    class DeleteMemberTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MEMBER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString()))
                .build();
        }

        @Test
        void deleteMemberShouldReturnErrorWhenTeamMailboxDoesNotExists() {
            Map<String, Object> errors = given()
                .delete("/" + BOB.asString())
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
                .containsEntry("message", "The requested team mailbox does not exists")
                .containsEntry("details", TEAM_MAILBOX.mailboxPath().asString() + " can not be found");
        }

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#usernameInvalidSource")
        void deleteMemberShouldReturnErrorWhenInvalidUser(String username) {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Map<String, Object> errors = given()
                .delete("/" + username)
            .then()
                .statusCode(BAD_REQUEST_400)
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
        void deleteMemberShouldRemoveAssignEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, BOB)).block();
            given()
                .delete("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .doesNotContain(BOB);
        }

        @Test
        void deleteMemberShouldReturn204StatusWhenUserAlreadyDoesNotInTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .put("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteMemberShouldNotRemoveUnAssignEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, BOB)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, ANDRE)).block();
            given()
                .delete("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .contains(ANDRE)
                .doesNotContain(BOB);
        }
    }
}
