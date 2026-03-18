/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                  *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.webadmin.vault;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Set;

import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.Domain;
import org.apache.james.core.MaybeSender;
import org.apache.james.json.DTOConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.SubscriptionManager;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMailboxManager;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.blob.deduplication.BlobStoreFactory;
import org.apache.james.task.Hostname;
import org.apache.james.task.MemoryTaskManager;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.vault.blob.BlobStoreDeletedMessageVault;
import org.apache.james.vault.blob.BucketNameGenerator;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
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
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import io.restassured.RestAssured;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

public class TeamMailboxDeletedMessagesVaultRoutesTest {

    private static final String TEAM_MAILBOX_ADDRESS = "marketing@linagora.com";
    private static final TeamMailbox TEAM_MAILBOX =
        OptionConverters.toJava(TeamMailbox.fromJava(Domain.of("linagora.com"), "marketing")).orElseThrow();
    private static final byte[] MESSAGE_CONTENT = "Subject: test\r\n\r\ntest body".getBytes(StandardCharsets.UTF_8);

    private WebAdminServer webAdminServer;
    private MemoryTaskManager taskManager;
    private TeamMailboxRepository teamMailboxRepository;
    private InMemoryMailboxManager mailboxManager;
    private DeletedMessageVault vault;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        SubscriptionManager subscriptionManager = new StoreSubscriptionManager(
            mailboxManager.getMapperFactory(),
            mailboxManager.getMapperFactory(),
            mailboxManager.getEventBus());

        teamMailboxRepository = new TeamMailboxRepositoryImpl(
            mailboxManager,
            subscriptionManager,
            mailboxManager.getMapperFactory(),
            Set.of(new TeamMailboxAutocompleteCallback(new InMemoryEmailAddressContactSearchEngine())));

        PlainBlobId.Factory blobIdFactory = new PlainBlobId.Factory();
        MemoryBlobStoreDAO blobStoreDAO = new MemoryBlobStoreDAO();
        UpdatableTickingClock clock = new UpdatableTickingClock(ZonedDateTime.parse("2024-01-01T00:00:00Z").toInstant());
        var blobStore = BlobStoreFactory.builder()
            .blobStoreDAO(blobStoreDAO)
            .blobIdFactory(blobIdFactory)
            .defaultBucketName()
            .passthrough();
        vault = new BlobStoreDeletedMessageVault(
            new RecordingMetricFactory(),
            new MemoryDeletedMessageMetadataVault(),
            blobStore,
            blobStoreDAO,
            new BucketNameGenerator(clock),
            clock,
            VaultConfiguration.ENABLED_DEFAULT);

        TeamMailboxRestoreService restoreService = new TeamMailboxRestoreService(vault, mailboxManager, VaultConfiguration.ENABLED_DEFAULT);

        taskManager = new MemoryTaskManager(new Hostname("foo"));
        JsonTransformer jsonTransformer = new JsonTransformer();

        TasksRoutes tasksRoutes = new TasksRoutes(taskManager, jsonTransformer,
            DTOConverter.of(TeamMailboxVaultRestoreTaskAdditionalInformationDTO.SERIALIZATION_MODULE));

        TeamMailboxDeletedMessagesVaultRoutes routes = new TeamMailboxDeletedMessagesVaultRoutes(
            restoreService, teamMailboxRepository, jsonTransformer, taskManager);

        webAdminServer = WebAdminUtils.createWebAdminServer(routes, tasksRoutes).start();

        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminServer)
            .setBasePath(TeamMailboxDeletedMessagesVaultRoutes.ROOT_PATH)
            .build();
    }

    @AfterEach
    void stop() {
        webAdminServer.destroy();
        taskManager.stop();
    }

    @Test
    void restoreShouldReturn400WhenTeamMailboxAddressIsInvalid() {
        given()
            .queryParam("action", "restore")
        .when()
            .post("/notAnEmail")
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is("InvalidArgument"));
    }

    @Test
    void restoreShouldReturn400WhenActionParameterIsMissing() {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

        given()
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is("InvalidArgument"));
    }

    @Test
    void restoreShouldReturn400WhenActionParameterIsInvalid() {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

        given()
            .queryParam("action", "invalid")
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
        .then()
            .statusCode(HttpStatus.BAD_REQUEST_400)
            .contentType(JSON)
            .body("statusCode", is(HttpStatus.BAD_REQUEST_400))
            .body("type", is("InvalidArgument"));
    }

    @Test
    void restoreShouldReturn404WhenTeamMailboxDoesNotExist() {
        given()
            .queryParam("action", "restore")
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
        .then()
            .statusCode(HttpStatus.NOT_FOUND_404)
            .contentType(JSON)
            .body("statusCode", is(HttpStatus.NOT_FOUND_404))
            .body("type", is("notFound"));
    }

    @Test
    void restoreShouldReturn201WithTaskIdWhenTeamMailboxExists() {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

        given()
            .queryParam("action", "restore")
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .body("taskId", notNullValue());
    }

    @Test
    void restoreTaskShouldCompleteWithCorrectAdditionalInformationAndRestoreMessageToTeamMailboxFolder() throws Exception {
        Mono.from(teamMailboxRepository.createTeamMailbox(TEAM_MAILBOX)).block();

        DeletedMessage deletedMessage = DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(42))
            .originMailboxes(InMemoryId.of(1))
            .user(TEAM_MAILBOX.self())
            .deliveryDate(ZonedDateTime.parse("2024-01-01T00:00:00Z"))
            .deletionDate(ZonedDateTime.parse("2024-01-02T00:00:00Z"))
            .sender(MaybeSender.nullSender())
            .recipients()
            .hasAttachment(false)
            .size(MESSAGE_CONTENT.length)
            .build();
        Mono.from(vault.append(deletedMessage, new ByteArrayInputStream(MESSAGE_CONTENT))).block();

        String taskId = given()
            .queryParam("action", "restore")
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
            .jsonPath()
            .get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"))
            .body("taskId", is(taskId))
            .body("type", is("team-mailbox-deleted-messages-restore"))
            .body("additionalInformation.timestamp", notNullValue())
            .body("additionalInformation.type", is("team-mailbox-deleted-messages-restore"))
            .body("additionalInformation.teamMailboxAddress", is(TEAM_MAILBOX_ADDRESS))
            .body("additionalInformation.successfulRestoreCount", is(1))
            .body("additionalInformation.errorRestoreCount", is(0));

        MailboxSession ownerSession = mailboxManager.createSystemSession(TEAM_MAILBOX.owner());
        MailboxPath restorePath = TEAM_MAILBOX.mailboxPath(VaultConfiguration.ENABLED_DEFAULT.getRestoreLocation());
        long messageCount = mailboxManager.getMailbox(restorePath, ownerSession).getMessageCount(ownerSession);
        assertThat(messageCount).isEqualTo(1);
    }
}
