package com.linagora.tmail.james.jmap.model;

import static com.linagora.tmail.james.jmap.ElasticSearchContactConfiguration.DEFAULT_CONFIGURATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.backends.es.DockerElasticSearchExtension;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.IndexCreationFactory;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.ContactMappingFactory;
import com.linagora.tmail.james.jmap.ES6EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract;

public class ES6EmailAddressContactAutoCompleteTest implements EmailAddressContactSearchEngineContract {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility
        .with().pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .await();

    @RegisterExtension
    public final DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    ReactorElasticSearchClient client;
    ES6EmailAddressContactSearchEngine searchEngine;

    @BeforeEach
    void setUp() throws Exception {
        ContactMappingFactory contactMappingFactory = new ContactMappingFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION, DEFAULT_CONFIGURATION);
        client = elasticSearch.getDockerElasticSearch().clientProvider().get();

        createUserContactIndex(client, contactMappingFactory);
        createDomainContactIndex(client, contactMappingFactory);

        searchEngine = new ES6EmailAddressContactSearchEngine(client, DEFAULT_CONFIGURATION);
    }

    @Override
    public EmailAddressContactSearchEngine testee() {
        return searchEngine;
    }

    @Override
    public void awaitDocumentsIndexed(long documentCount) {
        CALMLY_AWAIT.atMost(Durations.TEN_SECONDS)
            .untilAsserted(() -> assertThat(client.search(
                    new SearchRequest(DEFAULT_CONFIGURATION.getUserContactIndexName().getValue(), DEFAULT_CONFIGURATION.getDomainContactIndexName().getValue())
                        .source(new SearchSourceBuilder().query(QueryBuilders.matchAllQuery())),
                    RequestOptions.DEFAULT)
                .block()
                .getHits().getTotalHits()).isEqualTo(documentCount));
    }

    private ReactorElasticSearchClient createUserContactIndex(ReactorElasticSearchClient client, ContactMappingFactory contactMappingFactory) throws IOException {
        return new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(DEFAULT_CONFIGURATION.getUserContactIndexName())
            .addAlias(DEFAULT_CONFIGURATION.getUserContactReadAliasName())
            .addAlias(DEFAULT_CONFIGURATION.getUserContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()), Optional.of(contactMappingFactory.userContactMappingContent()));
    }

    private ReactorElasticSearchClient createDomainContactIndex(ReactorElasticSearchClient client, ContactMappingFactory contactMappingFactory) throws IOException {
        return new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(DEFAULT_CONFIGURATION.getDomainContactIndexName())
            .addAlias(DEFAULT_CONFIGURATION.getDomainContactReadAliasName())
            .addAlias(DEFAULT_CONFIGURATION.getDomainContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()), Optional.of(contactMappingFactory.domainContactMappingContent()));
    }
}
