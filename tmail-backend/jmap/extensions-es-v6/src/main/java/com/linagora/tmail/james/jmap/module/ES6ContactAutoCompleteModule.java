package com.linagora.tmail.james.jmap.module;

import java.io.FileNotFoundException;
import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.es.ElasticSearchConfiguration;
import org.apache.james.backends.es.ReactorElasticSearchClient;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.user.api.UsernameChangeTaskStep;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.ES6EmailAddressContactSearchEngine;
import com.linagora.tmail.james.jmap.ElasticSearchContactConfiguration;
import com.linagora.tmail.james.jmap.contact.ContactUsernameChangeTaskStep;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

public class ES6ContactAutoCompleteModule extends AbstractModule {
    public static final String ELASTICSEARCH_CONFIGURATION_NAME = "elasticsearch";

    private static final Logger LOGGER = LoggerFactory.getLogger(ES6ContactAutoCompleteModule.class);

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
        bind(ES6EmailAddressContactSearchEngine.class).in(Scopes.SINGLETON);

        bind(EmailAddressContactSearchEngine.class).to(ES6EmailAddressContactSearchEngine.class);

        Multibinder.newSetBinder(binder(), UsernameChangeTaskStep.class)
            .addBinding()
            .to(ContactUsernameChangeTaskStep.class);
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
