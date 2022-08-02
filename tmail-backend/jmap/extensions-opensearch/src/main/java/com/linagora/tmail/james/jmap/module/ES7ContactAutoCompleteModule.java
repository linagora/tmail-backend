package com.linagora.tmail.james.jmap.module;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.opensearch.ElasticSearchConfiguration;
import org.apache.james.backends.opensearch.ReactorElasticSearchClient;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.ESEmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.ElasticSearchContactConfiguration;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

public class ES7ContactAutoCompleteModule extends AbstractModule {
    public static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";

    private static final Logger LOGGER = LoggerFactory.getLogger(ES7ContactAutoCompleteModule.class);

    static class ContactIndexCreator implements Startable {

        private final ElasticSearchConfiguration elasticSearchConfiguration;
        private final ElasticSearchContactConfiguration contactConfiguration;
        private final ReactorElasticSearchClient client;

        @Inject
        ContactIndexCreator(ElasticSearchConfiguration configuration,
                            ElasticSearchContactConfiguration contactConfiguration,
                            ReactorElasticSearchClient client) {
            this.elasticSearchConfiguration = configuration;
            this.contactConfiguration = contactConfiguration;
            this.client = client;
        }

        void createIndex() throws IOException {
            ContactIndexCreationUtil.createIndices(client, elasticSearchConfiguration, contactConfiguration);
        }
    }

    @Override
    protected void configure() {
        bind(ESEmailAddressContactSearchEngine.class).in(Scopes.SINGLETON);

        bind(EmailAddressContactSearchEngine.class).to(ESEmailAddressContactSearchEngine.class);
    }

    @Provides
    @Singleton
    private ElasticSearchContactConfiguration getElasticSearchContactConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration configuration = propertiesProvider.getConfiguration(ELASTICSEARCH_CONFIGURATION_NAME);
            return ElasticSearchContactConfiguration.fromProperties(configuration);
        } catch (FileNotFoundException e) {
            LOGGER.warn("Could not find " + ELASTICSEARCH_CONFIGURATION_NAME + " configuration file. Using default contact configuration");
            return ElasticSearchContactConfiguration.DEFAULT_CONFIGURATION;
        }
    }

    @ProvidesIntoSet
    InitializationOperation createIndex(ContactIndexCreator instance) {
        return InitilizationOperationBuilder
            .forClass(ContactIndexCreator.class)
            .init(instance::createIndex);
    }
}
