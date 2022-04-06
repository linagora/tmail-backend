package com.linagora.tmail.james;

import static com.linagora.tmail.james.jmap.ElasticSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.james.CassandraExtension;
import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.apache.james.mailbox.opendistro.DockerOpenDistroExtension;
import org.apache.james.mailbox.opendistro.DockerOpenDistroSingleton;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.apache.james.modules.RabbitMQExtension;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.common.LinagoraContactAutocompleteMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceContactAutocompleteModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

public class DistributedLinagoraContactAutoCompleteMethodTest implements LinagoraContactAutocompleteMethodContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    DockerOpenDistroExtension openDistroExtension = new DockerOpenDistroExtension(DockerOpenDistroSingleton.INSTANCE);

    @RegisterExtension
    static JamesServerExtension testExtension = new JamesServerBuilder<DistributedJamesConfiguration>(tmpDir ->
        DistributedJamesConfiguration.builder()
            .workingDirectory(tmpDir)
            .configurationFromClasspath()
            .blobStore(BlobStoreConfiguration.builder()
                .disableCache()
                .deduplication()
                .noCryptoConfig()
                .disableSingleSave())
            .build())
        .extension(new DockerOpenDistroExtension(DockerOpenDistroSingleton.INSTANCE))
        .extension(new CassandraExtension())
        .extension(new RabbitMQExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapGuiceContactAutocompleteModule()))
        .build();

    private final ReactorElasticSearchClient client = openDistroExtension.getDockerOpenDistro().clientProvider().get();

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Override
    public void awaitDocumentsIndexed(long documentCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest(DEFAULT_CONFIGURATION.getUserContactIndexName().getValue(), DEFAULT_CONFIGURATION.getDomainContactIndexName().getValue())
                        .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
                    RequestOptions.DEFAULT)
                .block()
                .getHits().getTotalHits().value).isEqualTo(documentCount));
    }
}
