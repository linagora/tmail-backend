package com.linagora.tmail.james.jmap.module;

import java.io.IOException;
import java.util.Optional;

import org.apache.james.backends.opensearch.IndexCreationFactory;
import org.apache.james.backends.opensearch.OpenSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorOpenSearchClient;

import com.linagora.tmail.james.jmap.ContactMappingFactory;
import com.linagora.tmail.james.jmap.OpenSearchContactConfiguration;

public class ContactIndexCreationUtil {
    public static void createIndices(ReactorOpenSearchClient client,
                                     OpenSearchConfiguration openSearchConfiguration,
                                     OpenSearchContactConfiguration contactConfiguration) throws IOException {
        ContactMappingFactory contactMappingFactory = new ContactMappingFactory(openSearchConfiguration, contactConfiguration);
        new IndexCreationFactory(openSearchConfiguration)
            .useIndex(contactConfiguration.getUserContactIndexName())
            .addAlias(contactConfiguration.getUserContactReadAliasName())
            .addAlias(contactConfiguration.getUserContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()),
                Optional.of(contactMappingFactory.userContactMappingContent()));

        new IndexCreationFactory(openSearchConfiguration)
            .useIndex(contactConfiguration.getDomainContactIndexName())
            .addAlias(contactConfiguration.getDomainContactReadAliasName())
            .addAlias(contactConfiguration.getDomainContactWriteAliasName())
            .createIndexAndAliases(client, Optional.of(contactMappingFactory.generalContactIndicesSetting()),
                Optional.of(contactMappingFactory.domainContactMappingContent()));
    }
}
