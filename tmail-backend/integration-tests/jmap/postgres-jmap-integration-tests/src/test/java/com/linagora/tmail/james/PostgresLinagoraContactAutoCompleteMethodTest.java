package com.linagora.tmail.james;

import static com.linagora.tmail.james.jmap.OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.james.JamesServerExtension;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.jmap.rfc8621.contract.Fixture;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.linagora.tmail.james.app.DockerOpenSearchExtension;
import com.linagora.tmail.james.common.LinagoraContactAutocompleteMethodContract;
import com.linagora.tmail.james.common.module.JmapGuiceContactAutocompleteModule;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.OutboundMessage;

public class PostgresLinagoraContactAutoCompleteMethodTest implements LinagoraContactAutocompleteMethodContract {
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
    static JamesServerExtension testExtension = TmailJmapBase.JAMES_SERVER_EXTENSION_FUNCTION
        .apply(new JmapGuiceContactAutocompleteModule())
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
                "TmailExchange-AddressContactQueueForTesting",
                EMPTY_ROUTING_KEY,
                aqmpUserAddedMessage.getBytes(UTF_8))))
            .block();

        bobShouldHaveAndreContact();
    }
}
