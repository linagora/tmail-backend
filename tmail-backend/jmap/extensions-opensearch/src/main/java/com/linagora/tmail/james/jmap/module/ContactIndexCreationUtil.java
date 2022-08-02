package com.linagora.tmail.james.jmap.module;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.backends.opensearch.ElasticSearchConfiguration;
import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;

import com.linagora.tmail.james.jmap.ContactMappingFactory;
import com.linagora.tmail.james.jmap.ElasticSearchContactConfiguration;

public class ContactIndexCreationUtil {
    public static void createIndices(ReactorElasticSearchClient client,
                                     ElasticSearchConfiguration elasticSearchConfiguration,
                                     ElasticSearchContactConfiguration contactConfiguration) throws IOException {
        ContactMappingFactory contactMappingFactory = new ContactMappingFactory(elasticSearchConfiguration, contactConfiguration);
        new IndexCreationFactory(elasticSearchConfiguration)
            .useIndex(contactConfiguration.getUserContactIndexName())
            .addAlias(contactConfiguration.getUserContactReadAliasName())
            .addAlias(contactConfiguration.getUserContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()),
                Optional.of(contactMappingFactory.userContactMappingContent()));

        new IndexCreationFactory(elasticSearchConfiguration)
            .useIndex(contactConfiguration.getDomainContactIndexName())
            .addAlias(contactConfiguration.getDomainContactReadAliasName())
            .addAlias(contactConfiguration.getDomainContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()),
                Optional.of(contactMappingFactory.domainContactMappingContent()));
    }
}
