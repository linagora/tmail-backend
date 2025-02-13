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
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaManager;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.rrt.api.RecipientRewriteTableConfiguration;
import org.apache.james.rrt.memory.MemoryRecipientRewriteTable;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.jackson.QuotaModule;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxAutocompleteCallback;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.linagora.tmail.team.TeamMailboxUserEntityValidator;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import reactor.core.publisher.Mono;

public class TeamMailboxQuotaRoutesTest {
    private static final String BASE_PATH = "/domains/%s/team-mailboxes";
    private static final String QUOTA_PATH = "quota";
    private static final String LIMIT_PATH = QUOTA_PATH + "/limit";
    private static final String LIMIT_COUNT_PATH = LIMIT_PATH + "/count";
    private static final String SIZE_COUNT_PATH = LIMIT_PATH + "/size";
    private static final String COUNT = "count";
    private static final String SIZE = "size";

    private WebAdminServer webAdminServer;
    private TeamMailboxRepositoryImpl teamMailboxRepository;
    private MemoryUsersRepository usersRepository;
    private MemoryRecipientRewriteTable recipientRewriteTable;
    private MaxQuotaManager maxQuotaManager;
    private InMemoryCurrentQuotaManager currentQuotaManager;

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
        InMemoryMailboxManager mailboxManager = resources.getMailboxManager();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(),
                resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus());

        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, resources.getMailboxManager().getMapperFactory(),java.util.Set.of(new TeamMailboxAutocompleteCallback(new InMemoryEmailAddressContactSearchEngine())));

        UserEntityValidator validator = UserEntityValidator.aggregate(
            new DefaultUserEntityValidator(usersRepository),
            new RecipientRewriteTableUserEntityValidator(recipientRewriteTable),
            new TeamMailboxUserEntityValidator(teamMailboxRepository));

        usersRepository.setValidator(validator);
        recipientRewriteTable.setUsersRepository(usersRepository);
        recipientRewriteTable.setUserEntityValidator(validator);
        teamMailboxRepository.setValidator(validator);

        maxQuotaManager = resources.getMaxQuotaManager();
        QuotaManager quotaManager = resources.getQuotaManager();
        currentQuotaManager = resources.getCurrentQuotaManager();
        TeamMailboxQuotaService teamMailboxQuotaService = new TeamMailboxQuotaService(maxQuotaManager, quotaManager);

        QuotaModule quotaModule = new QuotaModule();
        JsonTransformer jsonTransformer = new JsonTransformer(quotaModule);

        TeamMailboxQuotaRoutes teamMailboxQuotaRoutes = new TeamMailboxQuotaRoutes(teamMailboxRepository, teamMailboxQuotaService, jsonTransformer, ImmutableSet.of(quotaModule));
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

    @Nested
    class Size {
        @Test
        void getSizeShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            when()
                .get("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getSizeShouldReturnNoContentByDefault() {
            when()
                .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);
        }

        @Test
        void getSizeShouldReturnStoredValue() throws MailboxException {
            long value = 42;
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(value));

            long quota =
                given()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .as(Long.class);

            assertThat(quota).isEqualTo(value);
        }

        @Test
        void putSizeShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            given()
                .body("123")
                .when()
                .put("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putSizeShouldRejectInvalid() {
            Map<String, Object> errors = with()
                .body("invalid")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
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
        void putSizeShouldSetToInfiniteWhenMinusOne() throws Exception {
            with()
                .body("-1")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaSizeLimit.unlimited());
        }

        @Test
        void putSizeShouldRejectNegativeOtherThanMinusOne() {
            Map<String, Object> errors = given()
                .body("-2")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
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
        void putSizeShouldAcceptValidValue() throws Exception {
            with()
                .body("42")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot())).contains(QuotaSizeLimit.size(42));
        }

        @Test
        void deleteSizeShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            when()
                .delete("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void deleteSizeShouldSetQuotaToEmpty() throws Exception {
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(42));

            with()
                .delete("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + SIZE_COUNT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot())).isEmpty();
        }
    }

    @Nested
    class PutQuota {
        @Test
        void putQuotaShouldReturnNotFoundWhenTeamMailboxDoesntExist() {
            when()
                .put("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void putQuotaWithNegativeCountShouldFail() {
            Map<String, Object> errors = with()
                .body("{\"count\":-2,\"size\":42}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
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
        void putQuotaWithNegativeCountShouldNotUpdatePreviousQuota() throws Exception {
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":-2,\"size\":42}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaWithNegativeSizeShouldFail() {
            Map<String, Object> errors = with()
                .body("{\"count\":52,\"size\":-3}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
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
        void putQuotaWithNegativeSizeShouldNotUpdatePreviousQuota() throws Exception {
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":52,\"size\":-3}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.BAD_REQUEST_400)
                .contentType(ContentType.JSON);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldUpdateBothQuota() throws Exception {
            with()
                .body("{\"count\":52,\"size\":42}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldRemoveCount() throws Exception {
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(52));

            with()
                .body("{\"count\":null,\"size\":42}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot()))
                .isEmpty();
            softly.assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaSizeLimit.size(42));
            softly.assertAll();
        }

        @Test
        void putQuotaShouldRemoveSize() throws Exception {
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":52,\"size\":null}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot()))
                .contains(QuotaCountLimit.count(52));
            softly.assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .isEmpty();
            softly.assertAll();
        }

        @Test
        void putQuotaShouldRemoveBoth() throws Exception {
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(52));
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(42));

            with()
                .body("{\"count\":null,\"size\":null}")
                .put("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + LIMIT_PATH)
            .then()
                .statusCode(HttpStatus.NO_CONTENT_204);

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(maxQuotaManager.getMaxMessage(TEAM_MAILBOX.quotaRoot()))
                .isEmpty();
            softly.assertThat(maxQuotaManager.getMaxStorage(TEAM_MAILBOX.quotaRoot()))
                .isEmpty();
            softly.assertAll();
        }
    }

    @Nested
    class GetQuota {
        @Test
        void getQuotaShouldReturnNotFoundWhenUserDoesntExist() {
            when()
                .get("/" + TEAM_MAILBOX_2.mailboxName().asString() + "/" + QUOTA_PATH)
            .then()
                .statusCode(HttpStatus.NOT_FOUND_404);
        }

        @Test
        void getQuotaShouldReturnBothWhenValueSpecified() throws Exception {
            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1111));
            maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(22));
            maxQuotaManager.setDomainMaxStorage(TEAM_MAILBOX_DOMAIN, QuotaSizeLimit.size(34));
            maxQuotaManager.setDomainMaxMessage(TEAM_MAILBOX_DOMAIN, QuotaCountLimit.count(23));
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(42));
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(52));

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(42);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("teamMailbox." + SIZE)).isEqualTo(42);
            softly.assertThat(jsonPath.getLong("teamMailbox." + COUNT)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("domain." + SIZE)).isEqualTo(34);
            softly.assertThat(jsonPath.getLong("domain." + COUNT)).isEqualTo(23);
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("global." + COUNT)).isEqualTo(22);
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnOccupation() throws Exception {
            QuotaOperation quotaIncrease = new QuotaOperation(TEAM_MAILBOX.quotaRoot(), QuotaCountUsage.count(20), QuotaSizeUsage.size(40));

            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(80));
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(100));
            currentQuotaManager.increase(quotaIncrease).block();

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("occupation.count")).isEqualTo(20);
            softly.assertThat(jsonPath.getLong("occupation.size")).isEqualTo(40);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.count")).isEqualTo(0.2);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.size")).isEqualTo(0.5);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.max")).isEqualTo(0.5);
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnOccupationWhenUnlimited() throws Exception {
            QuotaOperation quotaIncrease = new QuotaOperation(TEAM_MAILBOX.quotaRoot(), QuotaCountUsage.count(20), QuotaSizeUsage.size(40));

            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.unlimited());
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.unlimited());
            currentQuotaManager.increase(quotaIncrease).block();

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("occupation.count")).isEqualTo(20);
            softly.assertThat(jsonPath.getLong("occupation.size")).isEqualTo(40);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.count")).isEqualTo(0);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.size")).isEqualTo(0);
            softly.assertThat(jsonPath.getDouble("occupation.ratio.max")).isEqualTo(0);
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnOnlySpecifiedValues() throws Exception {
            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1111));
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(18));
            maxQuotaManager.setDomainMaxMessage(TEAM_MAILBOX_DOMAIN, QuotaCountLimit.count(52));

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(18);
            softly.assertThat(jsonPath.getLong("teamMailbox." + COUNT)).isEqualTo(18);
            softly.assertThat(jsonPath.getObject("teamMailbox." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain." + COUNT, Long.class)).isEqualTo(52);
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getObject("global." + COUNT, Long.class)).isNull();
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnGlobalValuesWhenNoTeamMailboxValuesDefined() throws Exception {
            maxQuotaManager.setGlobalMaxStorage(QuotaSizeLimit.size(1111));
            maxQuotaManager.setGlobalMaxMessage(QuotaCountLimit.count(12));

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("computed." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("computed." + COUNT)).isEqualTo(12);
            softly.assertThat(jsonPath.getObject("teamMailbox", Object.class)).isNull();
            softly.assertThat(jsonPath.getObject("domain", Object.class)).isNull();
            softly.assertThat(jsonPath.getLong("global." + SIZE)).isEqualTo(1111);
            softly.assertThat(jsonPath.getLong("global." + COUNT)).isEqualTo(12);
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnBothWhenValueSpecifiedAndEscaped() throws MailboxException {
            int maxStorage = 42;
            int maxMessage = 52;
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(maxStorage));
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(maxMessage));

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("teamMailbox." + SIZE)).isEqualTo(maxStorage);
            softly.assertThat(jsonPath.getLong("teamMailbox." + COUNT)).isEqualTo(maxMessage);
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnBothEmptyWhenDefaultValues() {
            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getObject(SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getObject(COUNT, Long.class)).isNull();
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnSizeWhenNoCount() throws MailboxException {
            int maxStorage = 42;
            maxQuotaManager.setMaxStorage(TEAM_MAILBOX.quotaRoot(), QuotaSizeLimit.size(maxStorage));

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getLong("teamMailbox." + SIZE)).isEqualTo(maxStorage);
            softly.assertThat(jsonPath.getObject("teamMailbox." + COUNT, Long.class)).isNull();
            softly.assertAll();
        }

        @Test
        void getQuotaShouldReturnBothWhenNoSize() throws MailboxException {
            int maxMessage = 42;
            maxQuotaManager.setMaxMessage(TEAM_MAILBOX.quotaRoot(), QuotaCountLimit.count(maxMessage));

            JsonPath jsonPath =
                when()
                    .get("/" + TEAM_MAILBOX.mailboxName().asString() + "/" + QUOTA_PATH)
                .then()
                    .statusCode(HttpStatus.OK_200)
                    .contentType(ContentType.JSON)
                    .extract()
                    .jsonPath();

            SoftAssertions softly = new SoftAssertions();
            softly.assertThat(jsonPath.getObject("teamMailbox." + SIZE, Long.class)).isNull();
            softly.assertThat(jsonPath.getLong("teamMailbox." + COUNT)).isEqualTo(maxMessage);
            softly.assertAll();
        }
    }
}
