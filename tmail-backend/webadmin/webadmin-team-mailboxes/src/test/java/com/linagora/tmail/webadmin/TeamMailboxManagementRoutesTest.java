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
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
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

import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxAutocompleteCallback;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.linagora.tmail.team.TeamMailboxUserEntityValidator;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import jakarta.mail.Flags;
import net.javacrumbs.jsonunit.core.Option;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TeamMailboxManagementRoutesTest {
    private static final String BASE_PATH = "/domains/%s/team-mailboxes";
    private static final String TEAM_MEMBER_BASE_PATH = BASE_PATH + "/%s/members";
    private static final String TEAM_MAILBOX_FOLDERS_BASE_PATH = BASE_PATH + "/%s/mailboxes";
    private static final String TEAM_MAILBOX_FOLDER_BASE_PATH = TEAM_MAILBOX_FOLDERS_BASE_PATH + "/%s";

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
    private InMemoryMailboxManager mailboxManager;
    private MemoryUsersRepository usersRepository;
    private MemoryRecipientRewriteTable recipientRewriteTable;
    private InMemoryEmailAddressContactSearchEngine emailAddressContactSearchEngine;

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

        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(),
                resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus());
        emailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine();

        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, resources.getMailboxManager().getMapperFactory(),java.util.Set.of(new TeamMailboxAutocompleteCallback(emailAddressContactSearchEngine)));

        UserEntityValidator validator = UserEntityValidator.aggregate(
            new DefaultUserEntityValidator(usersRepository),
            new RecipientRewriteTableUserEntityValidator(recipientRewriteTable),
            new TeamMailboxUserEntityValidator(teamMailboxRepository));

        usersRepository.setValidator(validator);
        recipientRewriteTable.setUsersRepository(usersRepository);
        recipientRewriteTable.setUserEntityValidator(validator);
        teamMailboxRepository.setValidator(validator);

        TeamMailboxManagementRoutes teamMailboxManagementRoutes = new TeamMailboxManagementRoutes(teamMailboxRepository,
            domainList, mailboxManager, new JsonTransformer());
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
                .containsEntry("message", "Invalid arguments supplied in the user request");
            assertThat((String) errors.get("details"))
                .contains("Domain parts ASCII chars must be a-z A-Z 0-9 - or _");
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
                .withOptions(Option.IGNORING_ARRAY_ORDER)
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
        void createTeamMailboxShouldIndexDomainContact() {
            given()
                .put("/marketing")
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Mono.from(emailAddressContactSearchEngine.list(Domain.of("linagora.com"))).block()
                .fields().address().asString())
                .isEqualTo("marketing@linagora.com");
        }

        @Test
        void createTeamMailboxShouldThrowForDomainNotFound() {
            Map<String, Object> errors = given()
                .basePath(String.format(BASE_PATH, "notfound.tld"))
                .put("/marketing")
            .then()
                .statusCode(NOT_FOUND_404)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The domain do not exist: notfound.tld");
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
        void deleteTeamMailboxShouldRemoveAssociatedDomainContact() {
            given()
                .put("/marketing")
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .delete("/marketing")
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(emailAddressContactSearchEngine.list(Domain.of("linagora.com")))
                .collectList()
                .block())
                .isEmpty();
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
                .containsEntry("message", "Invalid arguments supplied in the user request");
            assertThat((String) errors.get("details"))
                .contains("Domain parts ASCII chars must be a-z A-Z 0-9 - or _");
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
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();
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
                    "        \"username\": \"bob@linagora.com\"," +
                    "        \"role\": \"member\"" +
                    "    }" +
                    "]");
        }

        @Test
        void getTeamMailboxMembersShouldReturnListEntryWhenHasMultipleElement() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asManager(BOB))).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(ANDRE))).block();
            String response  = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(Option.IGNORING_ARRAY_ORDER)
                .isEqualTo("[" +
                    "    {" +
                    "        \"username\": \"bob@linagora.com\"," +
                    "        \"role\": \"manager\"" +
                    "    }," +
                    "    {" +
                    "        \"username\": \"andre@linagora.com\"," +
                    "        \"role\": \"member\"" +
                    "    }" +
                    "]");
        }

        @Test
        void getTeamMailboxMembersShouldNotReturnEntriesOfAnotherTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX_2)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX_2, TeamMailboxMember.asMember(BOB))).block();
            String response  = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .asString();

            assertThatJson(response)
                .withOptions(Option.IGNORING_ARRAY_ORDER)
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
                .queryParam("role", "member")
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
        void addMemberShouldReturnErrorWhenRoleIsInvalid() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Map<String, Object> errors = given()
                .queryParam("role", "invalid")
                .put("/" + BOB.asString())
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
                .containsEntry("message", "Wrong role: invalid");
        }

        @Test
        void addMemberShouldCreateNewWithRoleMemberWhenRoleDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .put("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .containsExactlyInAnyOrder(TeamMailboxMember.asMember(BOB));
        }

        @Test
        void addMemberShouldStoreAssignEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .queryParam("role", "member")
                .put("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .containsExactlyInAnyOrder(TeamMailboxMember.asMember(BOB));
        }

        @Test
        void addMemberShouldStoreAssignEntryWhenRoleIsManager() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .queryParam("role", "manager")
                .put("/" + BOB.asString())
                .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .containsExactlyInAnyOrder(TeamMailboxMember.asManager(BOB));
        }

        @Test
        void addMemberShouldReturn204StatusWhenUserAlreadyInTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();
            given()
                .queryParam("role", "member")
                .put("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void addMemberShouldUpdateRoleWhenUserAlreadyInTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();
            given()
                .queryParam("role", "manager")
                .put("/" + BOB.asString())
                .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .containsExactlyInAnyOrder(TeamMailboxMember.asManager(BOB));
        }
    }

    @Nested
    class GetTeamMailboxFoldersTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDERS_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString()))
                .build();
        }

        @Test
        void getTeamMailboxFoldersShouldReturn404WhenTeamMailboxDoesNotExist() {
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

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void getTeamMailboxFoldersShouldReturn400WhenNameIsInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDERS_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), teamMailboxName))
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
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void getTeamMailboxFoldersShouldReturnDefaultMailboxesAfterCreation() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            List<String> mailboxNames = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList("mailboxName");

            assertThat(mailboxNames)
                .containsExactlyInAnyOrder("marketing", "INBOX", "Sent", "Trash", "Outbox", "Drafts");
        }

        @Test
        void getTeamMailboxFoldersShouldReturnMailboxIdForEachFolder() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            List<String> mailboxIds = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList("mailboxId");

            assertThat(mailboxIds)
                .hasSize(6)
                .allSatisfy(id -> assertThat(id).isNotBlank());
        }

        @Test
        void getTeamMailboxFoldersShouldNotReturnFoldersOfAnotherTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX_2)).block();

            List<String> mailboxNames = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList("mailboxName");

            assertThat(mailboxNames).noneMatch(name -> name.startsWith("sale"));
        }
    }

    @Nested
    class CreateMailboxFolderTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDERS_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString()))
                .build();
        }

        @Test
        void createMailboxFolderShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .put("/MyFolder")
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
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void createMailboxFolderShouldReturn400WhenTeamMailboxNameIsInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDERS_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), teamMailboxName))
                .put("/MyFolder")
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
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void createMailboxFolderShouldReturn204AndCreateFolder() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .put("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);

            List<String> mailboxNames = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList("mailboxName");

            assertThat(mailboxNames).contains("MyFolder");
        }

        @Test
        void createMailboxFolderShouldBeIdempotent() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .put("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .put("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);
        }
    }

    @Nested
    class DeleteMailboxFolderTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDERS_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString()))
                .build();
        }

        @Test
        void deleteMailboxFolderShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .delete("/MyFolder")
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
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void deleteMailboxFolderShouldReturn400WhenTeamMailboxNameIsInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDERS_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), teamMailboxName))
                .delete("/MyFolder")
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
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void deleteMailboxFolderShouldReturn204AndDeleteFolder() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .put("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .delete("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);

            List<String> mailboxNames = given()
                .get()
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract()
                .body()
                .jsonPath()
                .getList("mailboxName");

            assertThat(mailboxNames).doesNotContain("MyFolder");
        }

        @Test
        void deleteMailboxFolderShouldBeIdempotent() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .delete("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);

            given()
                .delete("/MyFolder")
            .then()
                .statusCode(NO_CONTENT_204);
        }
    }

    @Nested
    class MessageCountTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "INBOX"))
                .build();
        }

        @Test
        void messageCountShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .get("/messageCount")
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
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void messageCountShouldReturn400WhenTeamMailboxNameIsInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), teamMailboxName, "INBOX"))
                .get("/messageCount")
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
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void messageCountShouldReturn404WhenFolderDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "NonExistentFolder"))
                .get("/messageCount")
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
                .containsEntry("message", "The requested mailbox folder does not exist");
        }

        @Test
        void messageCountShouldReturnZeroWhenFolderIsEmpty() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            String body = given()
                .get("/messageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body()
                .asString();

            assertThat(Long.parseLong(body.trim())).isEqualTo(0L);
        }

        @Test
        void messageCountShouldReturnCorrectCountAfterAppend() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.getMailbox(TEAM_MAILBOX.inboxPath(), session)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .build("Subject: test\r\n\r\nbody"), session);

            String body = given()
                .get("/messageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body()
                .asString();

            assertThat(Long.parseLong(body.trim())).isEqualTo(1L);
        }

        @Test
        void messageCountShouldReturnCorrectCountAfterAppendWithSeen() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.getMailbox(TEAM_MAILBOX.inboxPath(), session)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.SEEN))
                    .build("Subject: test\r\n\r\nbody"), session);

            String body = given()
                .get("/messageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body()
                .asString();

            assertThat(Long.parseLong(body.trim())).isEqualTo(1L);
        }
    }

    @Nested
    class UnseenMessageCountTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "INBOX"))
                .build();
        }

        @Test
        void unseenMessageCountShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .get("/unseenMessageCount")
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
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void unseenMessageCountShouldReturn400WhenTeamMailboxNameIsInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), teamMailboxName, "INBOX"))
                .get("/unseenMessageCount")
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
                .containsEntry("details", String.format("Predicate failed: '%s' contains some invalid characters. Should be [#a-zA-Z0-9-_] and no longer than 255 chars.", teamMailboxName));
        }

        @Test
        void unseenMessageCountShouldReturn404WhenFolderDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "NonExistentFolder"))
                .get("/unseenMessageCount")
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
                .containsEntry("message", "The requested mailbox folder does not exist");
        }

        @Test
        void unseenMessageCountShouldReturnZeroWhenFolderIsEmpty() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            String body = given()
                .get("/unseenMessageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body()
                .asString();

            assertThat(Long.parseLong(body.trim())).isEqualTo(0L);
        }

        @Test
        void unseenMessageCountShouldReturnCorrectCountAfterAppend() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.getMailbox(TEAM_MAILBOX.inboxPath(), session)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .build("Subject: test\r\n\r\nbody"), session);

            String body = given()
                .get("/unseenMessageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body()
                .asString();

            assertThat(Long.parseLong(body.trim())).isEqualTo(1L);
        }

        @Test
        void unseenMessageCountShouldReturnCorrectCountAfterAppendWithSeen() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.getMailbox(TEAM_MAILBOX.inboxPath(), session)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .withFlags(new Flags(Flags.Flag.SEEN))
                    .build("Subject: test\r\n\r\nbody"), session);

            String body = given()
                .get("/unseenMessageCount")
            .then()
                .statusCode(OK_200)
                .extract()
                .body()
                .asString();

            assertThat(Long.parseLong(body.trim())).isEqualTo(0L);
        }
    }

    @Nested
    class GetExtraAclTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "INBOX"))
                .build();
        }

        @Test
        void getExtraAclShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .get("/extraAcl")
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested team mailbox does not exists");
        }

        @ParameterizedTest
        @MethodSource("com.linagora.tmail.webadmin.TeamMailboxManagementRoutesTest#namespaceInvalidSource")
        void getExtraAclShouldReturn400WhenTeamMailboxNameIsInvalid(String teamMailboxName) {
            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), teamMailboxName, "INBOX"))
                .get("/extraAcl")
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument");
        }

        @Test
        void getExtraAclShouldReturn404WhenFolderDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "NonExistent"))
                .get("/extraAcl")
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested mailbox folder does not exist");
        }

        @Test
        void getExtraAclShouldReturnEmptyWhenNoExtraEntries() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> body = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(body).isEmpty();
        }

        @Test
        void getExtraAclShouldFilterTeamMailboxMembers() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, com.linagora.tmail.team.TeamMailboxMember.asMember(BOB))).block();

            Map<String, Object> body = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(body).doesNotContainKey(BOB.asString());
        }

        @Test
        void getExtraAclShouldReturnExtraEntryAfterSetExtraAcl() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .body("lr")
                .put("/extraAcl/" + ANDRE.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            Map<String, Object> body = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(body).containsEntry(ANDRE.asString(), "lr");
        }
    }

    @Nested
    class SetExtraAclUserTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "INBOX"))
                .build();
        }

        @Test
        void setExtraAclUserShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .body("lr")
                .put("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested team mailbox does not exists");
        }

        @Test
        void setExtraAclUserShouldReturn404WhenFolderDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "NonExistent"))
                .body("lr")
                .put("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested mailbox folder does not exist");
        }

        @Test
        void setExtraAclUserShouldReturn400WhenRightsAreInvalid() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .body("INVALID")
                .put("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument");
        }

        @Test
        void setExtraAclUserShouldReturn204AndStoreRights() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .body("lr")
                .put("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            Map<String, Object> acl = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .extract().body().jsonPath().getMap(".");

            assertThat(acl).containsEntry(BOB.asString(), "lr");
        }

        @Test
        void setExtraAclUserShouldReplaceExistingRights() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given().body("lr").put("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);
            given().body("lrs").put("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);

            Map<String, Object> acl = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .extract().body().jsonPath().getMap(".");

            assertThat(acl).containsEntry(BOB.asString(), "lrs");
        }

        @Test
        void setExtraAclUserShouldReturn400WhenTargetIsTeamMailboxMember() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();

            Map<String, Object> errors = given()
                .body("lr")
                .put("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", BOB.asString() + " is a team mailbox member and cannot be managed via extraAcl");
        }

        @Test
        void setExtraAclUserShouldReturn400WhenTargetIsSystemUser() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            String ownerUsername = TEAM_MAILBOX.owner().asString();
            Map<String, Object> errors = given()
                .body("lr")
                .put("/extraAcl/" + ownerUsername)
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", ownerUsername + " is a system user and cannot be managed via extraAcl");
        }
    }

    @Nested
    class DeleteExtraAclUserTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "INBOX"))
                .build();
        }

        @Test
        void deleteExtraAclUserShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .delete("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested team mailbox does not exists");
        }

        @Test
        void deleteExtraAclUserShouldReturn404WhenFolderDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "NonExistent"))
                .delete("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested mailbox folder does not exist");
        }

        @Test
        void deleteExtraAclUserShouldReturn204AndRemoveEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given().body("lr").put("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);

            given()
                .delete("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            Map<String, Object> acl = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .extract().body().jsonPath().getMap(".");

            assertThat(acl).doesNotContainKey(BOB.asString());
        }

        @Test
        void deleteExtraAclUserShouldBeIdempotent() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given().delete("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);
            given().delete("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteExtraAclUserShouldNotRemoveOtherEntries() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given().body("lr").put("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);
            given().body("lrs").put("/extraAcl/" + ANDRE.asString()).then().statusCode(NO_CONTENT_204);

            given().delete("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);

            Map<String, Object> acl = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .extract().body().jsonPath().getMap(".");

            assertThat(acl)
                .doesNotContainKey(BOB.asString())
                .containsEntry(ANDRE.asString(), "lrs");
        }

        @Test
        void deleteExtraAclUserShouldReturn400WhenTargetIsTeamMailboxMember() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();

            Map<String, Object> errors = given()
                .delete("/extraAcl/" + BOB.asString())
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", BOB.asString() + " is a team mailbox member and cannot be managed via extraAcl");
        }

        @Test
        void deleteExtraAclUserShouldReturn400WhenTargetIsSystemUser() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            String adminUsername = TEAM_MAILBOX.admin().asString();
            Map<String, Object> errors = given()
                .delete("/extraAcl/" + adminUsername)
            .then()
                .statusCode(BAD_REQUEST_400)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", adminUsername + " is a system user and cannot be managed via extraAcl");
        }
    }

    @Nested
    class DeleteExtraAclTest {

        @BeforeEach
        void setUp() {
            RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
                .setBasePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "INBOX"))
                .build();
        }

        @Test
        void deleteExtraAclShouldReturn404WhenTeamMailboxDoesNotExist() {
            Map<String, Object> errors = given()
                .delete("/extraAcl")
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested team mailbox does not exists");
        }

        @Test
        void deleteExtraAclShouldReturn404WhenFolderDoesNotExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            Map<String, Object> errors = given()
                .basePath(String.format(TEAM_MAILBOX_FOLDER_BASE_PATH, TEAM_MAILBOX_DOMAIN.asString(), TEAM_MAILBOX.mailboxName().asString(), "NonExistent"))
                .delete("/extraAcl")
            .then()
                .statusCode(NOT_FOUND_404)
                .contentType(JSON)
                .extract().body().jsonPath().getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", NOT_FOUND_404)
                .containsEntry("type", "notFound")
                .containsEntry("message", "The requested mailbox folder does not exist");
        }

        @Test
        void deleteExtraAclShouldReturn204AndRemoveAllExtraEntries() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given().body("lr").put("/extraAcl/" + BOB.asString()).then().statusCode(NO_CONTENT_204);
            given().body("lrs").put("/extraAcl/" + ANDRE.asString()).then().statusCode(NO_CONTENT_204);

            given()
                .delete("/extraAcl")
            .then()
                .statusCode(NO_CONTENT_204);

            Map<String, Object> acl = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .extract().body().jsonPath().getMap(".");

            assertThat(acl).isEmpty();
        }

        @Test
        void deleteExtraAclShouldPreserveTeamMailboxMembers() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, com.linagora.tmail.team.TeamMailboxMember.asMember(BOB))).block();

            given().body("lrs").put("/extraAcl/" + ANDRE.asString()).then().statusCode(NO_CONTENT_204);

            given().delete("/extraAcl").then().statusCode(NO_CONTENT_204);

            // Extra entry removed
            Map<String, Object> acl = given()
                .get("/extraAcl")
            .then()
                .statusCode(OK_200)
                .extract().body().jsonPath().getMap(".");

            assertThat(acl).doesNotContainKey(ANDRE.asString());
        }

        @Test
        void deleteExtraAclShouldBeIdempotent() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given().delete("/extraAcl").then().statusCode(NO_CONTENT_204);
            given().delete("/extraAcl").then().statusCode(NO_CONTENT_204);
        }
    }

    @Nested
    class RepositionSystemRightsTest {

        @Test
        void repositionSystemRightsShouldReturn400WhenNoAction() {
            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .post()
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void repositionSystemRightsShouldReturn400WhenInvalidAction() {
            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "unknownAction")
                .post()
            .then()
                .statusCode(BAD_REQUEST_400);
        }

        @Test
        void repositionSystemRightsShouldReturn204WhenNoTeamMailboxes() {
            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "repositionSystemRights")
                .post()
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void repositionSystemRightsShouldReturn204WhenTeamMailboxesExist() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "repositionSystemRights")
                .post()
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void repositionSystemRightsShouldRestoreAdminFullRights() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.applyRightsCommand(TEAM_MAILBOX.mailboxPath(),
                MailboxACL.command().forUser(TEAM_MAILBOX.admin())
                    .rights(MailboxACL.Rfc4314Rights.deserialize("lr")).asReplacement(),
                session);

            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "repositionSystemRights")
                .post()
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(mailboxManager.listRights(TEAM_MAILBOX.mailboxPath(), session)
                .getEntries().get(MailboxACL.EntryKey.createUserEntryKey(TEAM_MAILBOX.admin())))
                .isEqualTo(MailboxACL.FULL_RIGHTS);
        }

        @Test
        void repositionSystemRightsShouldRestoreSelfFullRights() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.applyRightsCommand(TEAM_MAILBOX.mailboxPath(),
                MailboxACL.command().forUser(TEAM_MAILBOX.self())
                    .rights(MailboxACL.Rfc4314Rights.deserialize("lr")).asReplacement(),
                session);

            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "repositionSystemRights")
                .post()
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(mailboxManager.listRights(TEAM_MAILBOX.mailboxPath(), session)
                .getEntries().get(MailboxACL.EntryKey.createUserEntryKey(TEAM_MAILBOX.self())))
                .isEqualTo(MailboxACL.FULL_RIGHTS);
        }

        @Test
        void repositionSystemRightsShouldApplyToAllSubFolders() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.applyRightsCommand(TEAM_MAILBOX.inboxPath(),
                MailboxACL.command().forUser(TEAM_MAILBOX.admin())
                    .rights(MailboxACL.Rfc4314Rights.deserialize("lr")).asReplacement(),
                session);

            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "repositionSystemRights")
                .post()
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(mailboxManager.listRights(TEAM_MAILBOX.inboxPath(), session)
                .getEntries().get(MailboxACL.EntryKey.createUserEntryKey(TEAM_MAILBOX.admin())))
                .isEqualTo(MailboxACL.FULL_RIGHTS);
        }

        @Test
        void repositionSystemRightsShouldWorkAcrossMultipleTeamMailboxes() throws Exception {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX_2)).block();
            MailboxSession session = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
            mailboxManager.applyRightsCommand(TEAM_MAILBOX.mailboxPath(),
                MailboxACL.command().forUser(TEAM_MAILBOX.admin())
                    .rights(MailboxACL.Rfc4314Rights.deserialize("lr")).asReplacement(),
                session);
            mailboxManager.applyRightsCommand(TEAM_MAILBOX_2.mailboxPath(),
                MailboxACL.command().forUser(TEAM_MAILBOX_2.admin())
                    .rights(MailboxACL.Rfc4314Rights.deserialize("lr")).asReplacement(),
                session);

            given()
                .basePath(TeamMailboxManagementRoutes.TEAM_MAILBOXES_BASE_PATH)
                .queryParam("action", "repositionSystemRights")
                .post()
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(mailboxManager.listRights(TEAM_MAILBOX.mailboxPath(), session)
                .getEntries().get(MailboxACL.EntryKey.createUserEntryKey(TEAM_MAILBOX.admin())))
                .isEqualTo(MailboxACL.FULL_RIGHTS);
            assertThat(mailboxManager.listRights(TEAM_MAILBOX_2.mailboxPath(), session)
                .getEntries().get(MailboxACL.EntryKey.createUserEntryKey(TEAM_MAILBOX_2.admin())))
                .isEqualTo(MailboxACL.FULL_RIGHTS);
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
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();
            given()
                .delete("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .doesNotContain(TeamMailboxMember.asMember(BOB));
        }

        @Test
        void deleteMemberShouldReturn204StatusWhenUserAlreadyDoesNotInTeamMailbox() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            given()
                .delete("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);
        }

        @Test
        void deleteMemberShouldNotRemoveUnAssignEntry() {
            Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();
            Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(ANDRE))).block();
            given()
                .delete("/" + BOB.asString())
            .then()
                .statusCode(NO_CONTENT_204);

            assertThat(Flux.from(teamMailboxRepository.listMembers(TEAM_MAILBOX)).collectList().block())
                .contains(TeamMailboxMember.asMember(ANDRE))
                .doesNotContain(TeamMailboxMember.asMember(BOB));
        }
    }
}
