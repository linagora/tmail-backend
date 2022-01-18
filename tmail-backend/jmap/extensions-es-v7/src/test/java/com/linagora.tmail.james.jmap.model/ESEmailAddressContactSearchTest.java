package com.linagora.tmail.james.jmap.model;

import static com.linagora.tmail.james.jmap.ElasticSearchContactConfiguration.DEFAULT_CONFIGURATION;

import org.apache.james.backends.es.v7.DockerElasticSearchExtension;
import org.apache.james.backends.es.v7.ElasticSearchConfiguration;
import org.apache.james.backends.es.v7.IndexCreationFactory;
import org.apache.james.backends.es.v7.ReactorElasticSearchClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.ESEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.EmailAddressContactMappingFactory;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngineContract;

@Disabled("ISSUE-316 Fails because we need to customize analysers attached to the INDEX. THis requires some tiny " +
    "modifications in James ES related source code.")
public class ESEmailAddressContactSearchTest implements EmailAddressContactSearchEngineContract {
    @RegisterExtension
    public final DockerElasticSearchExtension elasticSearch = new DockerElasticSearchExtension();

    ESEmailAddressContactSearchEngine searchEngine;

    @BeforeEach
    void setUp() throws Exception {
        ReactorElasticSearchClient client = elasticSearch.getDockerElasticSearch().clientProvider().get();
        new IndexCreationFactory(ElasticSearchConfiguration.DEFAULT_CONFIGURATION)
            .useIndex(DEFAULT_CONFIGURATION.getIndexName())
            .addAlias(DEFAULT_CONFIGURATION.getWriteAliasName())
            .addAlias(DEFAULT_CONFIGURATION.getReadAliasName())
            .createIndexAndAliases(client, EmailAddressContactMappingFactory.generateSetting());

        searchEngine = new ESEmailAddressContactSearchEngine(client, DEFAULT_CONFIGURATION);
    }

    @Override
    public EmailAddressContactSearchEngine testee() {
        return searchEngine;
    }
}
