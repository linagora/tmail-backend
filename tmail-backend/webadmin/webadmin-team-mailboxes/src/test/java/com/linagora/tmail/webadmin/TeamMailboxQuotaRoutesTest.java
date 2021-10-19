package com.linagora.tmail.webadmin;

import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_2;
import static com.linagora.tmail.webadmin.TeamMailboxFixture.TEAM_MAILBOX_DOMAIN;
import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.apache.james.DefaultUserEntityValidator;
import org.apache.james.RecipientRewriteTableUserEntityValidator;
import org.apache.james.UserEntityValidator;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.linagora.tmail.team.TeamMailboxUserEntityValidator;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import reactor.core.publisher.Mono;

public class TeamMailboxQuotaRoutesTest {
    private static final String BASE_PATH = "/domains/%s/team-mailboxes";
    private static final String LIMIT_COUNT_PATH = "quota/limit/count";

    private WebAdminServer webAdminServer;
    private TeamMailboxRepositoryImpl teamMailboxRepository;
    private MemoryUsersRepository usersRepository;
    private MemoryRecipientRewriteTable recipientRewriteTable;
    private MaxQuotaManager maxQuotaManager;

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
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager);

        UserEntityValidator validator = UserEntityValidator.aggregate(
            new DefaultUserEntityValidator(usersRepository),
            new RecipientRewriteTableUserEntityValidator(recipientRewriteTable),
            new TeamMailboxUserEntityValidator(teamMailboxRepository));

        usersRepository.setValidator(validator);
        recipientRewriteTable.setUsersRepository(usersRepository);
        recipientRewriteTable.setUserEntityValidator(validator);
        teamMailboxRepository.setValidator(validator);

        maxQuotaManager = new InMemoryPerUserMaxQuotaManager();
        TeamMailboxQuotaService teamMailboxQuotaService = new TeamMailboxQuotaService(maxQuotaManager);

        TeamMailboxQuotaRoutes teamMailboxQuotaRoutes = new TeamMailboxQuotaRoutes(teamMailboxRepository, teamMailboxQuotaService, new JsonTransformer());
        webAdminServer = WebAdminUtils.createWebAdminServer(teamMailboxQuotaRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(String.format(BASE_PATH, TEAM_MAILBOX_DOMAIN.asString()))
            .build();

        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
    }

    @Nested
    class Count {
        @Test
        void getCountShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            when()
                .get("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getCountShouldReturnNoContentByDefault() {
            given()
                .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getCountShouldReturnStoredValue() throws MailboxException {
            int value = 42;

            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(value));

            Long actual =
                given()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(Long.class);

            assertThat(actual).isEqualTo(value);
        }

        @Test
        void putCountShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            given()
                .body("invalid")
            .when()
                .put("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putCountShouldRejectInvalid() {
            Map<String, Object> errors = with()
                .body("invalid")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1")
                .containsEntry("details", "For input string: \"invalid\"");
        }

        @Test
        void putCountShouldSetToInfiniteWhenMinusOne() throws Exception {
            with()
                .body("-1")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot())).contains(QuotaCountLimit.unlimited());
        }

        @Test
        void putCountShouldRejectNegativeOtherThanMinusOne() {
            Map<String, Object> errors = with()
                .body("-2")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON)
                .extract()
                .body()
                .jsonPath()
                .getMap(".");

            assertThat(errors)
                .containsEntry("statusCode", HttpStatus.BAD_REQUEST_400)
                .containsEntry("type", "InvalidArgument")
                .containsEntry("message", "Invalid quota. Need to be an integer value greater or equal to -1");
        }

        @Test
        void putCountShouldAcceptValidValue() throws Exception {
            with()
                .body("42")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot())).contains(QuotaCountLimit.count(42));
        }

        @Test
        void deleteCountShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            when()
                .delete("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteCountShouldSetQuotaToEmpty() throws Exception {
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(42));

            with()
                .delete("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot())).isEmpty();
        }
    }
}
