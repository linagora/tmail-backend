package com.linagora.tmail.james;

import static com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration.BlobStoreImplName.S3;
import static com.linagora.tmail.james.app.TestRabbitMQModule.ADDRESS_CONTACT_EXCHANGE;
import static com.linagora.tmail.james.jmap.OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.james.JamesServerBuilder;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.apache.james.modules.AwsS3BlobStoreExtension;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.linagora.tmail.blob.blobid.list.BlobStoreConfiguration;
import com.linagora.tmail.james.app.CassandraExtension;
import com.linagora.tmail.james.app.DistributedJamesConfiguration;
import com.linagora.tmail.james.app.DistributedServer;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.app.EventBusKeysChoice;
import com.linagora.tmail.james.common.LinagoraContactAutocompleteMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceContactAutocompleteModule;
import com.linagora.tmail.module.LinagoraTestJMAPServerModule;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public class DistributedLinagoraContactAutoCompleteMethodTest implements LinagoraContactAutocompleteMethodContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();
    private static final com.linagora.tmail.james.app.RabbitMQExtension rabbitMQExtensionModule = new com.linagora.tmail.james.app.RabbitMQExtension();

    @RegisterExtension
    static DockerOpenSearchExtension opensearchExtension = new DockerOpenSearchExtension();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.dockerRabbitMQ(rabbitMQExtensionModule.dockerRabbitMQ())
        .restartPolicy(RabbitMQExtension.DockerRestartPolicy.PER_CLASS)
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

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
        .extension(new DockerOpenSearchExtension())
        .extension(new CassandraExtension())
        .extension(rabbitMQExtensionModule)
        .extension(new RedisExtension())
        .extension(new AwsS3BlobStoreExtension())
        .server(configuration -> DistributedServer.createServer(configuration)
            .overrideWith(new LinagoraTestJMAPServerModule())
            .overrideWith(new JmapGuiceContactAutocompleteModule()))
        .build();

    private final ReactorOpenSearchClient client = opensearchExtension.getDockerOS().clientProvider().get();

    @AfterEach
    void tearDown() throws IOException {
        client.close();
    }

    @Override
    public void awaitDocumentsIndexed(long documentCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                new SearchRequest.Builder()
                    .index(DEFAULT_CONFIGURATION.getUserContactIndexName().getValue(), DEFAULT_CONFIGURATION.getDomainContactIndexName().getValue())
                    .query(QueryBuilders.matchAll().build()._toQuery())
                    .build())
                .block()
                .hits().total().value()).isEqualTo(documentCount));
    }

    @Test
    void contactShouldBeIndexedWhenAQMPUserAddedMessage() {
        String aqmpUserAddedMessage = String.format("{ " +
            "   \"type\": \"addition\"," +
            "   \"scope\": \"user\", " +
            "   \"owner\" : \"%s\"," +
            "   \"entry\": {" +
            "        \"address\": \"%s\"," +
            "        \"firstname\": \"Alice\"," +
            "        \"surname\": \"Watson\"" +
            "    }" +
            "}", Fixture.BOB().asString(), Fixture.ANDRE().asString());

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(
                ADDRESS_CONTACT_EXCHANGE,
                EMPTY_ROUTING_KEY,
                aqmpUserAddedMessage.getBytes(UTF_8))))
            .block();

        bobShouldHaveAndreContact();
    }
}
