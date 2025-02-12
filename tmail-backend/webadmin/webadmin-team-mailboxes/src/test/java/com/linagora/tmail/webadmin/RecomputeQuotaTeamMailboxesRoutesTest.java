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

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jetty.http.HttpStatus.BAD_REQUEST_400;
import static org.eclipse.jetty.http.HttpStatus.NOT_FOUND_404;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.webadmin.Constants;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.contact.InMemoryEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.team.mailboxes.TeamMailboxAutocompleteCallback;
import com.linagora.tmail.team.TMailQuotaRootResolver;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxName;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesRoutes;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesService;
import com.linagora.tmail.webadmin.quota.recompute.RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;

public class RecomputeQuotaTeamMailboxesRoutesTest {
    private static final String TEAM_MAILBOX_DOMAIN = "linagora.com";
    private static final TeamMailbox TEAM_MAILBOX = TeamMailbox.apply(Domain.of(TEAM_MAILBOX_DOMAIN), TeamMailboxName.fromString("marketing").toOption().get());
    private static final String BASE_PATH = Constants.SEPARATOR + "domains" + Constants.SEPARATOR + TEAM_MAILBOX_DOMAIN + Constants.SEPARATOR + "team-mailboxes";

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private TeamMailboxRepository teamMailboxRepository;
    private CurrentQuotaManager currentQuotaManager;
    private SessionProvider sessionProvider;
    private InMemoryMailboxManager mailboxManager;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();
        mailboxManager = resources.getMailboxManager();
        sessionProvider = mailboxManager.getSessionProvider();
        MailboxSessionMapperFactory mailboxSessionMapperFactory = mailboxManager.getMapperFactory();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(),
                resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus());

        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager, subscriptionManager, resources.getMailboxManager().getMapperFactory(), java.util.Set.of(new TeamMailboxAutocompleteCallback(new InMemoryEmailAddressContactSearchEngine())));
        TMailQuotaRootResolver tMailQuotaRootResolver = new TMailQuotaRootResolver(
            sessionProvider,
            mailboxSessionMapperFactory,
            teamMailboxRepository);
        currentQuotaManager = resources.getCurrentQuotaManager();
        RecomputeQuotaTeamMailboxesService recomputeQuotaTeamMailboxesService = new RecomputeQuotaTeamMailboxesService(
            sessionProvider,
            tMailQuotaRootResolver,
            mailboxSessionMapperFactory,
            currentQuotaManager,
            teamMailboxRepository);

        TasksRoutes tasksRoutes = new TasksRoutes(taskManager,
            jsonTransformer,
            DTOConverter.of(RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO.SERIALIZATION_MODULE));

        RecomputeQuotaTeamMailboxesRoutes recomputeQuotaTeamMailboxesRoutes = new RecomputeQuotaTeamMailboxesRoutes(
            taskManager,
            jsonTransformer,
            recomputeQuotaTeamMailboxesService, teamMailboxRepository);

        webAdminServer = WebAdminUtils.createWebAdminServer(recomputeQuotaTeamMailboxesRoutes, tasksRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(BASE_PATH)
            .build();

        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void recomputeQuotaShouldReturnErrorWhenTaskParameterInvalid() {
        given()
            .queryParam("task", "invalid")
            .post()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'task' is missing or must be 'RecomputeQuotas'"));
    }

    @Test
    void recomputeQuotaShouldReturnErrorWhenMissingTaskParameter() {
        given()
            .post()
        .then()
            .statusCode(BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(BAD_REQUEST_400))
            .body("type", is("InvalidArgument"))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'task' is missing or must be 'RecomputeQuotas'"));
    }

    @Test
    void recomputeQuotaShouldReturnTaskId() {
        given()
            .queryParam("task", "RecomputeQuotas")
            .post()
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void recomputeQuotaTaskShouldReturnDetail() {
        String taskId = given()
            .queryParam("task", "RecomputeQuotas")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("startedDate", is(notNullValue()))
            .body("completedDate", is(notNullValue()))
            .body("submitDate", is(notNullValue()))
            .body("type", is("recompute-quota-team-mailboxes"))
            .body("type", is("recompute-quota-team-mailboxes"))
            .body("additionalInformation.timestamp", is(notNullValue()))
            .body("additionalInformation.type", is("recompute-quota-team-mailboxes"))
            .body("additionalInformation.domain", is("linagora.com"))
            .body("additionalInformation.failedQuotaRoots", is(empty()))
            .body("additionalInformation.processedQuotaRoots", is(1));
    }

    @Test
    void recomputeQuotaShouldReturnTaskIdEvenTeamMailboxDomainDoesNotExists() {
        Map<String, Object> errors = given()
            .queryParam("task", "RecomputeQuotas")
            .basePath(Constants.SEPARATOR + "domains" + Constants.SEPARATOR + "notfound" + Constants.SEPARATOR + "team-mailboxes")
            .post()
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
            .containsEntry("message", "The requested domain does not have any team mailbox");
    }

    @Test
    void recomputeQuotaShouldCompleteWhenAssignedTeamMailboxNoData() {
        String taskId = given()
            .queryParam("task", "RecomputeQuotas")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(TEAM_MAILBOX.quotaRoot()))
            .block())
            .isEqualTo(CurrentQuotas.emptyQuotas());
    }

    @Test
    void recomputeQuotaShouldCompleteWhenAssignedTeamMailboxHasData() throws Exception {
        MailboxSession session = sessionProvider.createSystemSession(TEAM_MAILBOX.owner());

        mailboxManager.getMailbox(TEAM_MAILBOX.inboxPath(), session)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setTo("bob@localhost.com")
                .setBody("This is a message123", StandardCharsets.UTF_8)), session);

        String taskId = given()
            .queryParam("task", "RecomputeQuotas")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("additionalInformation.failedQuotaRoots", is(empty()))
            .body("additionalInformation.processedQuotaRoots", is(1));

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(TEAM_MAILBOX.quotaRoot())).block())
            .isEqualTo(new CurrentQuotas(QuotaCountUsage.count(1L), QuotaSizeUsage.size(105L)));
    }

    @Test
    void recomputeQuotaShouldCompleteWhenAssignedDomainHasMultiTeamMailboxes() throws Exception {
        MailboxSession session = sessionProvider.createSystemSession(TEAM_MAILBOX.owner());

        mailboxManager.getMailbox(TEAM_MAILBOX.inboxPath(), session)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setTo("bob@localhost.com")
                .setBody("This is a message123", StandardCharsets.UTF_8)), session);

        TeamMailbox teamMailbox2 = TeamMailbox.apply(Domain.of(TEAM_MAILBOX_DOMAIN), TeamMailboxName.fromString("sale").toOption().get());
        Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox2)).block();

        mailboxManager.getMailbox(teamMailbox2.mailboxPath(), session)
            .appendMessage(MessageManager.AppendCommand.from(Message.Builder.of()
                .setTo("bob@localhost.com")
                .setBody("This is a message", StandardCharsets.UTF_8)), session);

        String taskId = given()
            .queryParam("task", "RecomputeQuotas")
        .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("additionalInformation.failedQuotaRoots", is(empty()))
            .body("additionalInformation.processedQuotaRoots", is(2));

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(TEAM_MAILBOX.quotaRoot())).block())
            .isEqualTo(new CurrentQuotas(QuotaCountUsage.count(1L), QuotaSizeUsage.size(105L)));

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(teamMailbox2.quotaRoot())).block())
            .isEqualTo(new CurrentQuotas(QuotaCountUsage.count(1L), QuotaSizeUsage.size(102L)));
    }

    @Test
    void recomputeQuotaShouldResetCurrentQuotasWhenIncorrectQuotas() {
        QuotaOperation quotaOperation = new QuotaOperation(TEAM_MAILBOX.quotaRoot(), QuotaCountUsage.count(3L), QuotaSizeUsage.size(390L));
        Mono.from(currentQuotaManager.increase(quotaOperation)).block();

        String taskId = given()
            .queryParam("task", "RecomputeQuotas")
        .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await");

        assertThat(Mono.from(currentQuotaManager.getCurrentQuotas(TEAM_MAILBOX.quotaRoot())).block())
            .isEqualTo(CurrentQuotas.emptyQuotas());
    }

}
