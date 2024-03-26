package com.linagora.tmail.integration.distributed;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.S3;
import static com.linagora.tmail.james.jmap.OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.utils.GuiceProbe;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.integration.UserDeletionIntegrationContract;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.app.RabbitMQExtension;
import com.linagora.tmail.james.common.module.JmapGuiceKeystoreManagerModule;
import com.linagora.tmail.james.common.module.JmapGuiceLabelModule;
import com.linagora.tmail.james.common.probe.JmapGuiceContactAutocompleteProbe;
import com.linagora.tmail.james.common.probe.JmapSettingsProbe;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedUserDeletionIntegrationTest extends UserDeletionIntegrationContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static DockerOpenSearchExtension opensearchExtension = new DockerOpenSearchExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .implementation(S3)
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .eventBusKeysChoice(EventBusKeysChoice.REDIS)
            .build())
        .extension(opensearchExtension)
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapGuiceKeystoreManagerModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(JmapGuiceContactAutocompleteProbe.class))
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding()
                .to(JmapSettingsProbe.class))
            .overrideWith(new JmapGuiceLabelModule()))
        .build();

    private final ReactorOpenSearchClient client = opensearchExtension.getDockerOS().clientProvider().get();

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Override
    public void awaitDocumentsIndexed(Long documentCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest.Builder()
                        .index(DEFAULT_CONFIGURATION.getUserContactIndexName().getValue(), DEFAULT_CONFIGURATION.getDomainContactIndexName().getValue())
                        .query(QueryBuilders.matchAll().build()._toQuery())
                        .build())
                .block()
                .hits().total().value()).isEqualTo(documentCount));
    }
}
