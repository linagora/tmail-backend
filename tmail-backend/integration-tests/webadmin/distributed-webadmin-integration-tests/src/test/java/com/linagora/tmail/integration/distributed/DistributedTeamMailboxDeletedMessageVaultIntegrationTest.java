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

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Domain;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxPath;
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
    private static final String TEAM_MAILBOX_ADDRESS = "marketing@" + DOMAIN;
    private static final TeamMailbox TEAM_MAILBOX =
        OptionConverters.toJava(TeamMailbox.fromJava(Domain.of(DOMAIN), "marketing")).orElseThrow();
    private static final byte[] MESSAGE_CONTENT = "Subject: deleted mail\r\n\r\ndeleted body".getBytes(StandardCharsets.UTF_8);

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
            }))
        .build();

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        server.getProbe(DataProbeImpl.class).addDomain(DOMAIN);
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
        Awaitility.await()
            .atMost(Duration.ofMinutes(1))
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
}
