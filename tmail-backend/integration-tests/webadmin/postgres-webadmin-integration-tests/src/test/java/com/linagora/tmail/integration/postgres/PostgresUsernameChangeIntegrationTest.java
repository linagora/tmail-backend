package com.linagora.tmail.integration.postgres;

import static com.linagora.tmail.james.jmap.OpenSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;

import org.apache.james.GuiceJamesServer;
import org.apache.james.JamesServerExtension;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.opensearch.client.opensearch._types.query_dsl.QueryBuilders;
import org.opensearch.client.opensearch.core.SearchRequest;

import com.linagora.tmail.integration.UsernameChangeIntegrationContract;
import com.linagora.tmail.james.app.DockerOpenSearchExtension;

public class PostgresUsernameChangeIntegrationTest extends UsernameChangeIntegrationContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    static DockerOpenSearchExtension opensearchExtension = new DockerOpenSearchExtension();

    @RegisterExtension
    static JamesServerExtension testExtension = PostgresWebAdminBase.JAMES_SERVER_EXTENSION_SUPPLIER.get()
        .extensions(opensearchExtension)
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

    @Test
    @Disabled("TODO https://github.com/linagora/tmail-backend/issues/994")
    void shouldAdaptContacts(GuiceJamesServer server) throws Exception {

    }
}
