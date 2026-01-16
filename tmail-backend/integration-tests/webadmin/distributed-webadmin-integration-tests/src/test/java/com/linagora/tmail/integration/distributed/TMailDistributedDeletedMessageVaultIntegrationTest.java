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

import static org.apache.james.jmap.JMAPTestingConstants.DOMAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.junit.categories.BasicFeature;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.util.Port;
import org.apache.james.utils.GuiceProbe;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.utils.WebAdminGuiceProbe;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.search.Query;
import org.apache.james.webadmin.WebAdminUtils;
import org.apache.james.webadmin.integration.vault.DeletedMessageVaultIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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

import io.restassured.config.ParamConfig;
import io.restassured.specification.RequestSpecification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class TMailDistributedDeletedMessageVaultIntegrationTest extends DeletedMessageVaultIntegrationTest {
    private static final DockerOpenSearchExtension OPENSEARCH_EXTENSION = new DockerOpenSearchExtension();
    private static final Username HOMER = Username.of("homer@" + DOMAIN);
    private static final byte[] CONTENT = "header: value\r\n\r\ncontent".getBytes(StandardCharsets.UTF_8);
    private static final ZonedDateTime DELIVERY_DATE = ZonedDateTime.parse("2007-12-03T10:15:30Z");
    private static final ZonedDateTime DELETION_DATE = ZonedDateTime.parse("2007-12-03T10:16:30Z");

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
        .extension(OPENSEARCH_EXTENSION)
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .extension(new ClockExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(TmailBlobStoreDeletedMessageVaultProbe.class)))
        .build();

    @Override
    protected void awaitSearchUpToDate() {
        OPENSEARCH_EXTENSION.await();
    }

    @Disabled("JAMES-2688 Unstable test")
    @Test
    @Tag(BasicFeature.TAG)
    @Override
    public void vaultExportShouldExportZipContainsVaultMessagesToShareeWhenImapDeletedMailbox(GuiceJamesServer jmapServer) {

    }

    @Test
    @Override
    @Disabled("TmailBlobStoreDeletedMessageVault does not support this case")
    public void vaultDeleteShouldDeleteAllMessagesHavingSameBlobContent() {

    }

    @Test
    void vaultPurgeShouldCleanupMessagesFromBothLegacyBucketsAndSingleBucket(GuiceJamesServer server, UpdatableTickingClock clock) throws Exception {
        TmailBlobStoreDeletedMessageVaultProbe vaultProbe = server.getProbe(TmailBlobStoreDeletedMessageVaultProbe.class);

        MessageId legacyMessageId = new CassandraMessageId.Factory().generate();
        MessageId singleBucketMessageId = new CassandraMessageId.Factory().generate();
        MailboxId mailboxId = CassandraId.timeBased();

        // Append message in the old bucket(s)
        clock.setInstant(Instant.parse("2007-12-03T10:15:30.00Z"));
        Mono.from(vaultProbe.getVault().appendV1(createDeletedMessage(legacyMessageId, mailboxId), new ByteArrayInputStream(CONTENT))).block();

        // Append message in the single bucket
        clock.setInstant(Instant.parse("2008-01-03T10:15:30.00Z"));
        Mono.from(vaultProbe.getVault().append(createDeletedMessage(singleBucketMessageId, mailboxId), new ByteArrayInputStream(CONTENT))).block();

        // Purge the expired messages
        clock.setInstant(Instant.parse("2026-01-03T10:15:30.00Z"));
        purgeVault(webAdminApi(server));

        List<DeletedMessage> remainingMessages = Flux.from(vaultProbe.getVault().search(HOMER, Query.ALL))
            .collectList()
            .block();
        assertThat(remainingMessages).isEmpty();
    }

    private static RequestSpecification webAdminApi(GuiceJamesServer server) {
        Port webAdminPort = server.getProbe(WebAdminGuiceProbe.class).getWebAdminPort();
        return WebAdminUtils.spec(webAdminPort)
            .config(WebAdminUtils.defaultConfig()
                .paramConfig(new ParamConfig().replaceAllParameters()));
    }

    private static DeletedMessage createDeletedMessage(MessageId messageId, MailboxId mailboxId) throws Exception {
        return DeletedMessage.builder()
            .messageId(messageId)
            .originMailboxes(mailboxId)
            .user(HOMER)
            .deliveryDate(DELIVERY_DATE)
            .deletionDate(DELETION_DATE)
            .sender(MaybeSender.of(new MailAddress("sender@" + DOMAIN)))
            .recipients(new MailAddress("recipient@" + DOMAIN))
            .hasAttachment(false)
            .size(CONTENT.length)
            .build();
    }

    private static void purgeVault(RequestSpecification webAdminApi) {
        String taskId =
            webAdminApi.with()
                .queryParam("scope", "expired")
                .delete("/deletedMessages")
                .jsonPath()
                .get("taskId");

        webAdminApi.with()
            .get("/tasks/" + taskId + "/await")
        .then()
            .body("status", is("completed"));
    }
}
