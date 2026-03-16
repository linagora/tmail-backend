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
import static io.restassured.RestAssured.with;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.ClockExtension;
import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.Username;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.MailboxProbeImpl;
import org.apache.james.probe.DataProbe;
import org.apache.james.util.streams.Limit;
import org.apache.james.utils.DataProbeImpl;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.routes.TasksRoutes;
import org.assertj.core.api.SoftAssertions;
import org.awaitility.Awaitility;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.guice.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.jmap.firebase.FirebaseModuleChooserConfiguration;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import io.restassured.RestAssured;

public class DistributedPopulateKeywordEmailQueryViewTaskIntegrationTest {
    public static class MailboxManagerProbe implements GuiceProbe {
        private final MailboxManager mailboxManager;

        @Inject
        public MailboxManagerProbe(MailboxManager mailboxManager) {
            this.mailboxManager = mailboxManager;
        }

        public MailboxManager getMailboxManager() {
            return mailboxManager;
        }
    }

    public static class KeywordEmailQueryViewProbe implements GuiceProbe {
        private final KeywordEmailQueryView keywordEmailQueryView;

        @Inject
        public KeywordEmailQueryViewProbe(KeywordEmailQueryView keywordEmailQueryView) {
            this.keywordEmailQueryView = keywordEmailQueryView;
        }

        public KeywordEmailQueryView getKeywordEmailQueryView() {
            return keywordEmailQueryView;
        }
    }

    private static final Username OWNER = Username.of("bob@domain.tld");
    private static final Username SHAREE = Username.of("alice@domain.tld");
    private static final Keyword FLAGGED = new Keyword("$flagged");
    private static final Keyword USER_KEYWORD = new Keyword("$custom");
    private static final Instant INTERNAL_DATE = Instant.parse("2026-03-16T08:00:00Z");

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
        .extension(new ClockExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(MailboxManagerProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class).addBinding().to(KeywordEmailQueryViewProbe.class)))
        .build();

    private MailboxProbeImpl mailboxProbe;

    @BeforeEach
    void setUp(GuiceJamesServer server) throws Exception {
        DataProbe dataProbe = server.getProbe(DataProbeImpl.class);
        mailboxProbe = server.getProbe(MailboxProbeImpl.class);

        dataProbe.addDomain(OWNER.getDomainPart().get().asString());
        dataProbe.addUser(OWNER.asString(), "secret");
        dataProbe.addUser(SHAREE.asString(), "secret");

        WebAdminGuiceProbe webAdminGuiceProbe = server.getProbe(WebAdminGuiceProbe.class);
        RestAssured.requestSpecification = WebAdminUtils.buildRequestSpecification(webAdminGuiceProbe.getWebAdminPort())
            .setUrlEncodingEnabled(false)
            .build();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    @Test
    void populateKeywordEmailQueryViewShouldPopulateOwnerAndSharee(GuiceJamesServer server) throws Exception {
        MailboxId ownerInboxId = mailboxProbe.createMailbox(MailboxConstants.USER_NAMESPACE, OWNER.asString(), MailboxConstants.INBOX);
        grantReadAccess(server.getProbe(MailboxManagerProbe.class).getMailboxManager(), ownerInboxId, SHAREE);

        ComposedMessageId composedMessageId = mailboxProbe.appendMessage(OWNER.asString(), MailboxPath.inbox(OWNER),
            new ByteArrayInputStream("Subject: test\r\n\r\nbody".getBytes(StandardCharsets.UTF_8)),
            Date.from(INTERNAL_DATE),
            false,
            flaggedAndUserKeywordFlags());

        String taskId = with()
            .post("/mailboxes?task=populateKeywordEmailQueryView")
            .jsonPath()
            .getString("taskId");

        Awaitility.await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(() -> given()
                .basePath(TasksRoutes.BASE)
                .when()
                .get(taskId + "/await")
                .then()
                .statusCode(HttpStatus.OK_200)
                .body("status", is("completed")));

        KeywordEmailQueryView keywordEmailQueryView = server.getProbe(KeywordEmailQueryViewProbe.class).getKeywordEmailQueryView();
        List<MessageId> ownerFlagged = keywordEmailQueryView.listMessagesByKeyword(OWNER, FLAGGED, options()).collectList().block();
        List<MessageId> ownerUserKeyword = keywordEmailQueryView.listMessagesByKeyword(OWNER, USER_KEYWORD, options()).collectList().block();
        List<MessageId> shareeFlagged = keywordEmailQueryView.listMessagesByKeyword(SHAREE, FLAGGED, options()).collectList().block();
        List<MessageId> shareeUserKeyword = keywordEmailQueryView.listMessagesByKeyword(SHAREE, USER_KEYWORD, options()).collectList().block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(ownerFlagged).containsExactly(composedMessageId.getMessageId());
            softly.assertThat(ownerUserKeyword).containsExactly(composedMessageId.getMessageId());
            softly.assertThat(shareeFlagged).containsExactly(composedMessageId.getMessageId());
            softly.assertThat(shareeUserKeyword).containsExactly(composedMessageId.getMessageId());
        });
    }

    private void grantReadAccess(MailboxManager mailboxManager, MailboxId mailboxId, Username sharee) throws Exception {
        MailboxSession ownerSession = mailboxManager.createSystemSession(OWNER);
        mailboxManager.applyRightsCommand(mailboxId,
            MailboxACL.command()
                .forUser(sharee)
                .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                .asAddition(),
            ownerSession);
    }

    private Flags flaggedAndUserKeywordFlags() {
        Flags flags = new Flags();
        flags.add(Flags.Flag.FLAGGED);
        flags.add(USER_KEYWORD.getFlagName());
        return flags;
    }

    private KeywordEmailQueryView.Options options() {
        return new KeywordEmailQueryView.Options(Optional.empty(), Optional.empty(), Limit.limit(10), false);
    }
}
