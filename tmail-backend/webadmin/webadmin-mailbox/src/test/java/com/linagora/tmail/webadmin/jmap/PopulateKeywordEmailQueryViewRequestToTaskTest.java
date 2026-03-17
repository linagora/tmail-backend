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

package com.linagora.tmail.webadmin.jmap;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static io.restassured.RestAssured.with;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.mail.Flags;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.dnsservice.api.DNSService;
import org.apache.james.domainlist.lib.DomainListConfiguration;
import org.apache.james.domainlist.memory.MemoryDomainList;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.task.TaskManager;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.util.streams.Limit;
import org.apache.james.webadmin.Routes;
import org.apache.james.webadmin.WebAdminServer;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.apache.james.webadmin.tasks.TaskFromRequestRegistry;
import org.apache.james.webadmin.utils.ErrorResponder;
import org.apache.james.webadmin.utils.JsonTransformer;
import org.assertj.core.api.SoftAssertions;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.projections.ConcernedKeywordsExtractor;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.MailboxReadRightsResolver;
import com.linagora.tmail.james.jmap.projections.MemoryKeywordEmailQueryView;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;
import spark.Service;

class PopulateKeywordEmailQueryViewRequestToTaskTest {
    private static final class JMAPRoutes implements Routes {
        private final KeywordEmailQueryViewPopulator populator;
        private final TaskManager taskManager;

        private JMAPRoutes(KeywordEmailQueryViewPopulator populator, TaskManager taskManager) {
            this.populator = populator;
            this.taskManager = taskManager;
        }

        @Override
        public String getBasePath() {
            return BASE_PATH;
        }

        @Override
        public void define(Service service) {
            service.post(BASE_PATH,
                TaskFromRequestRegistry.builder()
                    .registrations(new PopulateKeywordEmailQueryViewRequestToTask(populator))
                    .buildAsRoute(taskManager),
                new JsonTransformer());
        }
    }

    private static final Domain JAMES_ORG = Domain.of("james.org");
    private static final Username OWNER = Username.of("bob@james.org");
    private static final Username SHAREE = Username.of("alice@james.org");
    private static final Username SECOND_SHAREE = Username.of("charlie@james.org");
    private static final Instant INTERNAL_DATE = Instant.parse("2024-01-01T10:15:30Z");
    private static final Keyword FLAGGED = new Keyword("$flagged");
    private static final Keyword DELETED = new Keyword("$deleted");
    private static final Keyword RECENT = new Keyword("$recent");
    private static final Keyword SEEN = new Keyword("$seen");
    private static final Keyword USER_KEYWORD = new Keyword("project-a");
    private static final String TEAM_MAILBOX_NAME = "marketing";
    private static final String BASE_PATH = "/mailboxes";

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private InMemoryMailboxManager mailboxManager;
    private InMemoryIntegrationResources resources;
    private MailboxSession ownerSession;
    private MailboxId ownerInboxId;
    private MemoryKeywordEmailQueryView keywordEmailQueryView;
    private TeamMailboxRepository teamMailboxRepository;

    @BeforeEach
    void setUp() throws Exception {
        JsonTransformer jsonTransformer = new JsonTransformer();
        taskManager = new MemoryTaskManager(new Hostname("foo"));

        resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        DNSService dnsService = mock(DNSService.class);
        MemoryDomainList domainList = new MemoryDomainList(dnsService);
        domainList.configure(DomainListConfiguration.DEFAULT);
        domainList.addDomain(JAMES_ORG);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(OWNER, "pass");
        usersRepository.addUser(SHAREE, "pass");
        usersRepository.addUser(SECOND_SHAREE, "pass");

        ownerSession = mailboxManager.createSystemSession(OWNER);
        ownerInboxId = mailboxManager.createMailbox(MailboxPath.inbox(OWNER), ownerSession).get();
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager,
            new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus()),
            resources.getMailboxManager().getMapperFactory(),
            Set.of());

        keywordEmailQueryView = new MemoryKeywordEmailQueryView();
        webAdminServer = WebAdminUtils.createWebAdminServer(
            new TasksRoutes(taskManager, jsonTransformer,
                DTOConverter.of(PopulateKeywordEmailQueryViewTaskAdditionalInformationDTO.module())),
            new JMAPRoutes(new KeywordEmailQueryViewPopulator(usersRepository, mailboxManager, keywordEmailQueryView,
                new MailboxReadRightsResolver(new UnionMailboxACLResolver()),
                new ConcernedKeywordsExtractor(),
                teamMailboxRepository), taskManager))
            .start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath("/mailboxes")
            .build();
    }

    @AfterEach
    void tearDown() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void actionRequestParameterShouldBeCompulsory() {
        when()
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter is compulsory. Supported values are [populateKeywordEmailQueryView]"));
    }

    @Test
    void postShouldFailUponInvalidAction() {
        given()
            .queryParam("action", "invalid")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Invalid value supplied for query parameter 'action': invalid. Supported values are [populateKeywordEmailQueryView]"));
    }

    @Test
    void postShouldFailUponEmptyAction() {
        given()
            .queryParam("action", "")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'action' query parameter cannot be empty or blank. Supported values are [populateKeywordEmailQueryView]"));
    }

    @Test
    void postShouldFailWhenMessagesPerSecondIsNotAnInt() {
        given()
            .queryParam("action", "populateKeywordEmailQueryView")
            .queryParam("messagesPerSecond", "abc")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("Illegal value supplied for query parameter 'messagesPerSecond', expecting a strictly positive optional integer"));
    }

    @Test
    void postShouldFailWhenMessagesPerSecondIsZero() {
        given()
            .queryParam("action", "populateKeywordEmailQueryView")
            .queryParam("messagesPerSecond", "0")
            .post()
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .body("statusCode", is(400))
            .body("type", is(ErrorResponder.ErrorType.INVALID_ARGUMENT.getType()))
            .body("message", is("Invalid arguments supplied in the user request"))
            .body("details", is("'messagesPerSecond' must be strictly positive"));
    }

    @Test
    void runningOptionsAndCountsShouldBePartOfTaskDetails() throws Exception {
        appendMessage(asFlags(List.of(Flags.Flag.FLAGGED), USER_KEYWORD));

        String taskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .queryParam("messagesPerSecond", "20")
            .post()
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .body("taskId", is(taskId))
            .body("type", is("PopulateKeywordEmailQueryViewTask"))
            .body("additionalInformation.runningOptions.messagesPerSecond", is(20))
            .body("additionalInformation.processedUserCount", is(3))
            .body("additionalInformation.processedMessageCount", is(1))
            .body("additionalInformation.failedUserCount", is(0))
            .body("additionalInformation.failedMessageCount", is(0))
            .body("additionalInformation.provisionedKeywordViewCount", is(2));
    }

    @Test
    void populateShouldProvisionConcernedKeywordsForOwnerAndSharees() throws Exception {
        shareInboxWithSharee(SHAREE);
        shareInboxWithSharee(SECOND_SHAREE);
        MessageId messageId = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED), USER_KEYWORD)).getId().getMessageId();

        String taskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageIdsByKeywordView(OWNER, FLAGGED)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(OWNER, USER_KEYWORD)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SHAREE, FLAGGED)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SHAREE, USER_KEYWORD)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SECOND_SHAREE, FLAGGED)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SECOND_SHAREE, USER_KEYWORD)).containsExactly(messageId);
        });
    }

    @Test
    void populateShouldProvisionConcernedKeywordsForTeamMailboxMember() throws Exception {
        TeamMailbox teamMailbox = createTeamMailbox(TEAM_MAILBOX_NAME);
        addMemberToTeamMailbox(teamMailbox, SHAREE);
        addMemberToTeamMailbox(teamMailbox, SECOND_SHAREE);
        MessageId messageId = appendMessageToTeamInbox(teamMailbox, asFlags(List.of(Flags.Flag.FLAGGED), USER_KEYWORD)).getId().getMessageId();

        String taskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageIdsByKeywordView(SHAREE, FLAGGED)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SHAREE, USER_KEYWORD)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SECOND_SHAREE, FLAGGED)).containsExactly(messageId);
            softly.assertThat(messageIdsByKeywordView(SECOND_SHAREE, USER_KEYWORD)).containsExactly(messageId);
        });
    }

    @Test
    void populateShouldNotProvisionKeywordForNonTeamMailboxMember() throws Exception {
        TeamMailbox teamMailbox = createTeamMailbox(TEAM_MAILBOX_NAME);
        addMemberToTeamMailbox(teamMailbox, SHAREE);
        appendMessageToTeamInbox(teamMailbox, asFlags(List.of(Flags.Flag.FLAGGED), USER_KEYWORD));

        String taskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageIdsByKeywordView(SECOND_SHAREE, FLAGGED)).isEmpty();
            softly.assertThat(messageIdsByKeywordView(SECOND_SHAREE, USER_KEYWORD)).isEmpty();
        });
    }

    @Test
    void populateShouldNotProvisionNonConcernedSystemFlags() throws Exception {
        appendMessage(asFlags(List.of(Flags.Flag.SEEN, Flags.Flag.DELETED, Flags.Flag.RECENT)));

        String taskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(messageIdsByKeywordView(OWNER, SEEN)).isEmpty();
            softly.assertThat(messageIdsByKeywordView(OWNER, DELETED)).isEmpty();
            softly.assertThat(messageIdsByKeywordView(OWNER, RECENT)).isEmpty();
        });
    }

    @Test
    void populateShouldNotProvisionKeywordForNonShareeUser() throws Exception {
        shareInboxWithSharee(SHAREE);
        appendMessage(asFlags(List.of(Flags.Flag.FLAGGED)));

        String taskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");

        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId + "/await");

        assertThat(messageIdsByKeywordView(SECOND_SHAREE, FLAGGED)).isEmpty();
    }

    @Test
    void populateShouldBeIdempotent() throws Exception {
        MessageId messageId = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED))).getId().getMessageId();

        String taskId1 = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId1 + "/await");

        String taskId2 = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(taskId2 + "/await");

        assertThat(messageIdsByKeywordView(OWNER, FLAGGED)).containsOnly(messageId);
    }

    @Test
    void populateShouldRemoveKeywordViewOfDeletedMessages() throws Exception {
        MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED)));
        MessageId messageId = appendResult.getId().getMessageId();

        String firstTaskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(firstTaskId + "/await");

        assertThat(messageIdsByKeywordView(OWNER, FLAGGED)).containsExactly(messageId);

        expungeMessage(appendResult);

        String secondTaskId = with()
            .queryParam("action", "populateKeywordEmailQueryView")
            .post()
            .jsonPath()
            .get("taskId");
        with()
            .basePath(TasksRoutes.BASE)
            .get(secondTaskId + "/await");

        assertThat(messageIdsByKeywordView(OWNER, FLAGGED)).isEmpty();
    }

    private void shareInboxWithSharee(Username sharee) throws Exception {
        resources.getStoreRightManager().applyRightsCommand(ownerInboxId,
            MailboxACL.command().forUser(sharee)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition(),
            ownerSession);
    }

    private MessageManager.AppendResult appendMessage(Flags flags) throws Exception {
        return ownerInbox().appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(Date.from(INTERNAL_DATE))
            .withFlags(flags)
            .notRecent()
            .build("Subject: test\r\n\r\nbody"), ownerSession);
    }

    private void expungeMessage(MessageManager.AppendResult appendResult) throws Exception {
        ownerInbox().setFlags(new Flags(Flags.Flag.DELETED), MessageManager.FlagsUpdateMode.ADD,
            MessageRange.one(appendResult.getId().getUid()), ownerSession);
        ownerInbox().expunge(MessageRange.one(appendResult.getId().getUid()), ownerSession);
    }

    private TeamMailbox createTeamMailbox(String teamMailboxName) {
        TeamMailbox teamMailbox = OptionConverters.toJava(TeamMailbox.fromJava(JAMES_ORG, teamMailboxName)).orElseThrow();
        Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox)).block();
        return teamMailbox;
    }

    private void addMemberToTeamMailbox(TeamMailbox teamMailbox, Username username) {
        Mono.from(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.asMember(username))).block();
    }

    private MessageManager.AppendResult appendMessageToTeamInbox(TeamMailbox teamMailbox, Flags flags) throws Exception {
        MailboxSession teamOwnerSession = mailboxManager.createSystemSession(teamMailbox.owner());
        return teamInbox(teamMailbox, teamOwnerSession).appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(Date.from(INTERNAL_DATE))
            .withFlags(flags)
            .notRecent()
            .build("Subject: test\r\n\r\nbody"), teamOwnerSession);
    }

    private MessageManager ownerInbox() throws Exception {
        return mailboxManager.getMailbox(ownerInboxId, ownerSession);
    }

    private MessageManager teamInbox(TeamMailbox teamMailbox, MailboxSession teamOwnerSession) throws Exception {
        return mailboxManager.getMailbox(teamMailbox.inboxPath(), teamOwnerSession);
    }

    private Flags asFlags(Keyword... flags) {
        Flags result = new Flags();
        for (Keyword flag : flags) {
            result.add(flag.getFlagName());
        }
        return result;
    }

    private Flags asFlags(List<Flags.Flag> systemFlags, Keyword... userFlags) {
        Flags result = new Flags();
        systemFlags.forEach(result::add);
        result.add(asFlags(userFlags));
        return result;
    }

    private List<MessageId> messageIdsByKeywordView(Username username, Keyword keyword) {
        return Flux.from(keywordEmailQueryView.listMessagesByKeyword(username, keyword, options()))
            .collectList()
            .block();
    }

    private KeywordEmailQueryView.Options options() {
        return new KeywordEmailQueryView.Options(Optional.empty(), Optional.empty(), Limit.limit(10), false);
    }
}
