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
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.assertThatJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.eclipse.jetty.http.HttpStatus.OK_200;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxAutocompleteCallback;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

public class UserTeamMailboxRoutesTest {
    private static final String BASE_PATH = "/users/%s/team-mailboxes";

    private WebAdminServer webAdminServer;
    private TeamMailboxRepository teamMailboxRepository;
    private UsersRepository usersRepository;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();

        InMemoryMailboxManager mailboxManager = resources.getMailboxManager();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(),
                resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus());

        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, java.util.Set.of(new TeamMailboxAutocompleteCallback(new InMemoryEmailAddressContactSearchEngine())));
        usersRepository = mock(UsersRepository.class);
        UserTeamMailboxRoutes userTeamMailboxRoutes = new UserTeamMailboxRoutes(teamMailboxRepository, new JsonTransformer(), usersRepository);
        webAdminServer = WebAdminUtils.createWebAdminServer(userTeamMailboxRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(String.format(BASE_PATH, BOB.asString()))
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Test
    void getTeamMailboxesShouldReturnErrorWhenUsernameDoesNotFound() {
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
            .containsEntry("message", "User bob@linagora.com does not exist");
    }

    @Test
    void getTeamMailboxesShouldReturnEmptyByDefault() throws UsersRepositoryException {
        when(usersRepository.contains(BOB)).thenReturn(true);
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
    @ValueSource(strings = {"@", "bob@domain@com"})
    void getTeamMailboxesShouldReturnErrorWhenUsernameInvalid(String username) throws UsersRepositoryException {
        when(usersRepository.contains(BOB)).thenReturn(true);
        
        Map<String, Object> errors = given()
            .basePath(String.format(BASE_PATH, username))
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
    }

    @Test
    void getTeamMailboxesShouldReturnEntriesWhenHasSingleElement() throws UsersRepositoryException {
        when(usersRepository.contains(BOB)).thenReturn(true);
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
        Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();

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
    void getTeamMailboxesShouldReturnEntriesWhenHasMultipleElement() throws UsersRepositoryException {
        when(usersRepository.contains(BOB)).thenReturn(true);
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX_2)).block();
        Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(BOB))).block();
        Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX_2, TeamMailboxMember.asMember(BOB))).block();
        
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

    @Test
    void getTeamMailboxesShouldNotReturnEntriesWhichUserIsNotMemberOf() throws UsersRepositoryException {
        when(usersRepository.contains(BOB)).thenReturn(true);
        when(usersRepository.contains(ANDRE)).thenReturn(true);
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
        Mono.from(teamMailboxRepository.addMember(TEAM_MAILBOX, TeamMailboxMember.asMember(ANDRE))).block();
        
        String response = given()
            .get()
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
