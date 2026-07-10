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

package com.linagora.tmail.integration.distributed;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Iterator;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.integration.probe.TmailBlobStoreDeletedMessageVaultProbe;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxProbe;
import com.linagora.tmail.webadmin.vault.TeamMailboxDeletedMessagesVaultRoutes;

import io.restassured.RestAssured;
import reactor.core.publisher.Flux;
import scala.jdk.javaapi.OptionConverters;

class DistributedTeamMailboxDeletedMessageVaultIntegrationTest {

    static class MailboxMessageCountProbe implements GuiceProbe {
        private final MailboxManager mailboxManager;

        @Inject
        public MailboxMessageCountProbe(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
        }

        public long getMessageCount(MailboxPath path) {
            try {
                MailboxSession session = mailboxManager.createSystemSession(path.getUser());
                return mailboxManager.getMailbox(path, session).getMessageCount(session);
            } catch (MailboxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static final String DOMAIN = "linagora.com";
    private static final Username ANDRE = Username.fromLocalPartWithDomain("andre", Domain.of(DOMAIN));
    private static final Username BOB = Username.fromLocalPartWithDomain("bob", Domain.of(DOMAIN));
    private static final String TEAM_MAILBOX_ADDRESS = "marketing@" + DOMAIN;
    private static final String PASSWORD = "secret";
    private static final String PERSONAL_BOX = "Archive";
    private static final String TEAM_FOLDER = "Projects";
    private static final ConditionFactory WAIT_AT_MOST_ONE_MINUTE = Awaitility.await()
        .pollInterval(Duration.ofMillis(100))
        .atMost(Duration.ofMinutes(1));
    private static final TeamMailbox TEAM_MAILBOX =
        OptionConverters.toJava(TeamMailbox.fromJava(Domain.of(DOMAIN), "marketing")).orElseThrow();
    private static final byte[] MESSAGE_CONTENT = "Subject: deleted mail\r\n\r\ndeleted body".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SECOND_MESSAGE_CONTENT = "Subject: deleted mail 2\r\n\r\ndeleted body 2".getBytes(StandardCharsets.UTF_8);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .s3()
                .noSecondaryS3BlobStore()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .firebaseModuleChooserConfiguration(FirebaseModuleChooserConfiguration.DISABLED)
            .build())
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> {
                Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TeamMailboxProbe.class);
                Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(TmailBlobStoreDeletedMessageVaultProbe.class);
                Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxMessageCountProbe.class);
                Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(DistributedPopulateKeywordEmailQueryViewTaskIntegrationTest.MailboxManagerProbe.class);
            }))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addDomain(DOMAIN);
        server.getProbe(DataProbeImpl.class).addUser(ANDRE.asString(), PASSWORD);
        server.getProbe(DataProbeImpl.class).addUser(BOB.asString(), PASSWORD);
        server.getProbe(TeamMailboxProbe.class).create(TEAM_MAILBOX);

        RestAssured.requestSpecification = WebAdminUtils
            .buildRequestSpecification(server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort())
            .setBasePath(TeamMailboxDeletedMessagesVaultRoutes.ROOT_PATH)
            .build();
    }

    @Test
    void restoreEndpointShouldBeExposed() {
        given()
            .queryParam("action", "restore")
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
        .then()
            .statusCode(HttpStatus.CREATED_201)
            .contentType(JSON)
            .body("taskId", notNullValue());
    }

    @Test
    void restoreTaskShouldRestoreVaultedMessagesToTeamMailboxRestoreFolder(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);

        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(
            TEAM_MAILBOX.owner().asString(),
            TEAM_MAILBOX.inboxPath(),
            new ByteArrayInputStream(MESSAGE_CONTENT),
            new Date(),
            false,
            new Flags());

        mailboxProbe.deleteMessage(
            ImmutableList.of(composedMessageId.getUid()),
            TEAM_MAILBOX.inboxPath(),
            TEAM_MAILBOX.owner());

        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);
        WAIT_AT_MOST_ONE_MINUTE
            .until(() -> Flux.from(vaultProbe.getVault().search(TEAM_MAILBOX.self(), Query.ALL))
                .count()
                .block() > 0);

        String taskId = given()
            .queryParam("action", "restore")
        .when()
            .post("/" + TEAM_MAILBOX_ADDRESS)
            .jsonPath().get("taskId");

        given()
            .basePath(TasksRoutes.BASE)
        .when()
            .get(taskId + "/await")
        .then()
            .statusCode(HttpStatus.OK_200)
            .body("status", is("completed"))
            .body("type", is("team-mailbox-deleted-messages-restore"))
            .body("additionalInformation.teamMailboxAddress", is(TEAM_MAILBOX_ADDRESS))
            .body("additionalInformation.successfulRestoreCount", is(1))
            .body("additionalInformation.errorRestoreCount", is(0));

        MailboxPath restorePath = TEAM_MAILBOX.mailboxPath(VaultConfiguration.DEFAULT.getRestoreLocation());
        assertThat(server.getProbe(MailboxMessageCountProbe.class).getMessageCount(restorePath))
            .isEqualTo(1);
    }

    @Test
    void expungeMessageShouldAppendToTeamMailboxVaultWhenMemberStillHasAnotherReference(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);

        // GIVEN a team mailbox member
        addMember(server, BOB);

        // AND a personal mailbox for that member
        mailboxProbe.createMailbox(MailboxPath.forUser(BOB, PERSONAL_BOX));

        // AND a team mailbox message copied into the member personal mailbox
        ComposedMessageId teamMessage = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), MESSAGE_CONTENT);
        mailboxProbe.copy(BOB, TEAM_MAILBOX.inboxPath(), MailboxPath.forUser(BOB, PERSONAL_BOX), teamMessage.getUid());

        // WHEN the member expunges the message from the team mailbox
        mailboxProbe.deleteMessage(ImmutableList.of(teamMessage.getUid()), TEAM_MAILBOX.inboxPath(), BOB);

        // THEN the message is appended to the team mailbox vault and not to the member personal vault
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 1);
        assertThat(vaultCount(vaultProbe, BOB)).isZero();
    }

    @Test
    void expungeMessageShouldNotAppendToMemberPersonalVault(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);

        // GIVEN a team mailbox member
        addMember(server, BOB);

        // AND a team mailbox message
        ComposedMessageId teamMessage = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), MESSAGE_CONTENT);

        // WHEN the member expunges the message from the team mailbox
        mailboxProbe.deleteMessage(ImmutableList.of(teamMessage.getUid()), TEAM_MAILBOX.inboxPath(), BOB);

        // THEN the message is not appended to the member personal vault
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 1);
        assertThat(vaultCount(vaultProbe, BOB)).isZero();
    }

    @Test
    void mailboxDeletionShouldAppendDeletedMessagesToTeamMailboxVault(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);
        MailboxPath teamFolderPath = TEAM_MAILBOX.mailboxPath(TEAM_FOLDER);

        // GIVEN a team mailbox folder
        mailboxProbe.createMailbox(teamFolderPath);

        // AND two messages in that folder
        appendMessage(mailboxProbe, teamFolderPath, MESSAGE_CONTENT);
        appendMessage(mailboxProbe, teamFolderPath, SECOND_MESSAGE_CONTENT);

        // WHEN the team mailbox folder is deleted
        mailboxProbe.deleteMailbox(teamFolderPath.getNamespace(), teamFolderPath.getUser().asString(), teamFolderPath.getName());

        // THEN both messages are appended to the team mailbox vault
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 2);
    }

    @Test
    void mailboxDeletionShouldAppendToTeamMailboxVaultWhenMemberStillHasAnotherReference(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);
        MailboxPath teamFolderPath = TEAM_MAILBOX.mailboxPath(TEAM_FOLDER);

        // GIVEN a team mailbox member
        addMember(server, BOB);

        // AND a team mailbox folder
        mailboxProbe.createMailbox(teamFolderPath);

        // AND a personal mailbox for that member
        mailboxProbe.createMailbox(MailboxPath.forUser(BOB, PERSONAL_BOX));

        // AND a team mailbox folder message copied into the member personal mailbox
        ComposedMessageId teamMessage = appendMessage(mailboxProbe, teamFolderPath, MESSAGE_CONTENT);
        mailboxProbe.copy(BOB, teamFolderPath, MailboxPath.forUser(BOB, PERSONAL_BOX), teamMessage.getUid());

        // WHEN the team mailbox folder is deleted
        mailboxProbe.deleteMailbox(teamFolderPath.getNamespace(), teamFolderPath.getUser().asString(), teamFolderPath.getName());

        // THEN the message is appended to the team mailbox vault and not to the member personal vault
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 1);
        assertThat(vaultCount(vaultProbe, BOB)).isZero();
    }

    @Test
    void personalDeleteShouldNotAppendToMemberVaultWhileTeamMailboxReferenceStillExists(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);
        MailboxPath personalBox = MailboxPath.forUser(BOB, PERSONAL_BOX);

        // GIVEN a team mailbox member
        addMember(server, BOB);

        // AND a personal mailbox for that member
        mailboxProbe.createMailbox(personalBox);

        // AND a team mailbox message copied into the member personal mailbox
        ComposedMessageId teamMessage = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), MESSAGE_CONTENT);
        mailboxProbe.copy(BOB, TEAM_MAILBOX.inboxPath(), personalBox, teamMessage.getUid());
        MessageUid copiedUid = findFirstMessageUid(server.getProbe(DistributedPopulateKeywordEmailQueryViewTaskIntegrationTest.MailboxManagerProbe.class).getMailboxManager(), BOB, personalBox);

        // WHEN the member deletes the copied personal message while the team mailbox still contains it
        mailboxProbe.deleteMessage(ImmutableList.of(copiedUid), personalBox, BOB);

        // THEN the member personal vault remains empty
        awaitVaultCount(vaultProbe, BOB, 0);

        // AND the team mailbox vault remains empty
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 0);
    }

    @Test
    void personalDeleteShouldAppendToMemberVaultWhenTeamMailboxReferenceNoLongerExists(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);
        MailboxPath personalBox = MailboxPath.forUser(BOB, PERSONAL_BOX);

        // GIVEN a team mailbox member
        addMember(server, BOB);

        // AND a personal mailbox for that member
        mailboxProbe.createMailbox(personalBox);

        // AND a team mailbox message copied into the member personal mailbox
        ComposedMessageId teamMessage = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), MESSAGE_CONTENT);
        mailboxProbe.copy(BOB, TEAM_MAILBOX.inboxPath(), personalBox, teamMessage.getUid());
        MessageUid copiedUid = findFirstMessageUid(server.getProbe(DistributedPopulateKeywordEmailQueryViewTaskIntegrationTest.MailboxManagerProbe.class).getMailboxManager(), BOB, personalBox);

        // AND the team mailbox reference is deleted first
        mailboxProbe.deleteMessage(ImmutableList.of(teamMessage.getUid()), TEAM_MAILBOX.inboxPath(), TEAM_MAILBOX.owner());
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 1);

        // WHEN the member deletes the copied personal message later
        mailboxProbe.deleteMessage(ImmutableList.of(copiedUid), personalBox, BOB);

        // THEN the copied message is appended to the member personal vault
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 1);
        // AND team mailbox vault remains the same
        awaitVaultCount(vaultProbe, BOB, 1);
    }

    @Test
    void personalDeleteShouldAppendToMemberVaultAfterMembershipRevocationWhileTeamMailboxMessageStillExists(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);
        MailboxPath personalBox = MailboxPath.forUser(BOB, PERSONAL_BOX);

        // AND a team mailbox member owning a personal mailbox
        addMember(server, BOB);
        mailboxProbe.createMailbox(personalBox);

        // AND a team mailbox message copied into the member personal mailbox
        ComposedMessageId teamMessage = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), MESSAGE_CONTENT);
        mailboxProbe.copy(BOB, TEAM_MAILBOX.inboxPath(), personalBox, teamMessage.getUid());
        MessageUid copiedUid = findFirstMessageUid(server.getProbe(DistributedPopulateKeywordEmailQueryViewTaskIntegrationTest.MailboxManagerProbe.class).getMailboxManager(), BOB, personalBox);

        // AND the copied-message owner is removed from the team mailbox membership while the team mailbox still keeps the message
        removeMember(server, BOB);

        // WHEN that user deletes the copied personal message
        mailboxProbe.deleteMessage(ImmutableList.of(copiedUid), personalBox, BOB);

        // THEN the user personal DMV contains the message
        awaitVaultCount(vaultProbe, BOB, 1);

        // AND the team mailbox vault remains empty because the team mailbox message still exists
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 0);
    }

    @Test
    void managerAndMemberShouldBehaveTheSameForTeamMailboxDmvRouting(GuiceJamesServer server) throws Exception {
        MailboxProbeImpl mailboxProbe = server.getProbe(MailboxProbeImpl.class);
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);

        // GIVEN a team mailbox manager and a regular member
        addManager(server, ANDRE);
        addMember(server, BOB);

        // AND two messages in the team mailbox
        ComposedMessageId message1 = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), MESSAGE_CONTENT);
        ComposedMessageId message2 = appendMessage(mailboxProbe, TEAM_MAILBOX.inboxPath(), SECOND_MESSAGE_CONTENT);

        // WHEN the manager deletes one message from the team mailbox
        mailboxProbe.deleteMessage(ImmutableList.of(message1.getUid()), TEAM_MAILBOX.inboxPath(), ANDRE);

        // AND the regular member deletes another message from the team mailbox
        mailboxProbe.deleteMessage(ImmutableList.of(message2.getUid()), TEAM_MAILBOX.inboxPath(), BOB);

        // THEN both deleted messages are appended under the team mailbox identity
        awaitVaultCount(vaultProbe, TEAM_MAILBOX.self(), 2);

        // AND neither personal DMV contains the deleted messages
        awaitVaultCount(vaultProbe, ANDRE, 0);
        awaitVaultCount(vaultProbe, BOB, 0);
    }

    private void addMember(GuiceJamesServer server, Username username) {
        server.getProbe(TeamMailboxProbe.class).addMember(TEAM_MAILBOX, username);
    }

    private void addManager(GuiceJamesServer server, Username username) {
        server.getProbe(TeamMailboxProbe.class).addManager(TEAM_MAILBOX, username);
    }

    private void removeMember(GuiceJamesServer server, Username username) {
        server.getProbe(TeamMailboxProbe.class).removeMember(TEAM_MAILBOX, username);
    }

    private ComposedMessageId appendMessage(MailboxProbeImpl mailboxProbe, MailboxPath mailboxPath, byte[] content) throws Exception {
        return mailboxProbe.appendMessage(
            TEAM_MAILBOX.owner().asString(),
            mailboxPath,
            new ByteArrayInputStream(content),
            new Date(),
            false,
            new Flags());
    }

    private long vaultCount(TmailBlobStoreDeletedMessageVaultProbe vaultProbe, Username username) {
        return Flux.from(vaultProbe.getVault().search(username, Query.ALL))
            .count()
            .block();
    }

    private void awaitVaultCount(TmailBlobStoreDeletedMessageVaultProbe vaultProbe, Username username, long expectedCount) {
        WAIT_AT_MOST_ONE_MINUTE
            .untilAsserted(() -> assertThat(vaultCount(vaultProbe, username)).isEqualTo(expectedCount));
    }

    private MessageUid findFirstMessageUid(MailboxManager mailboxManager, Username username, MailboxPath mailboxPath) throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(username);
        Iterator<MessageResult> messages = mailboxManager.getMailbox(mailboxPath, session)
            .getMessages(MessageRange.all(), FetchGroup.MINIMAL, session);

        assertThat(messages.hasNext()).isTrue();
        return messages.next().getUid();
    }
}
