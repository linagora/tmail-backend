package com.linagora.tmail;

import java.io.FileNotFoundException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.carddav.CardDavClient;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.contact.OpenPaasContactsConsumer;
import com.linagora.tmail.contact.OpenPaasContactsConsumerReconnectionHandler;
import com.linagora.tmail.carddav.CardDavClient.DefaultImpl.CardDavConfiguration;

public class OpenPaasModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasModule.class);
    public static final String OPENPAAS_INJECTION_KEY = "openpaas";
    public static final String OPENPAAS_CONFIGURATION_NAME = "openpaas";

    @ProvidesIntoSet
    public InitializationOperation initializeContactsConsumer(OpenPaasContactsConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(OpenPaasContactsConsumer.class)
            .init(instance::start);
    }

    @Provides
    @Named(OPENPAAS_CONFIGURATION_NAME)
    @Singleton
    public Configuration providePropertiesConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            return propertiesProvider.getConfiguration(OPENPAAS_CONFIGURATION_NAME);
        } catch (FileNotFoundException e) {
            LOGGER.error("Could not find configuration file '{}.properties'",
                OPENPAAS_CONFIGURATION_NAME);
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    public OpenPaasConfiguration provideOpenPaasConfiguration(@Named(OPENPAAS_CONFIGURATION_NAME) Configuration propertiesConfiguration) {
        return OpenPaasConfiguration.from(propertiesConfiguration);
    }

    @Provides
    public OpenPaasRestClient provideOpenPaasRestCLient(OpenPaasConfiguration openPaasConfiguration) {
        return new OpenPaasRestClient(openPaasConfiguration);
    }

    @Provides
    @Singleton
    public CardDavConfiguration provideCardDavConfiguration(@Named(OPENPAAS_CONFIGURATION_NAME) Configuration propertiesConfiguration) {
        return CardDavConfiguration.from(propertiesConfiguration);
    }

    @Provides
    @Singleton
    public CardDavClient provideCardDavClient(CardDavConfiguration cardDavConfiguration) {
        return new CardDavClient.DefaultImpl(cardDavConfiguration);
    }

    @Provides
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public RabbitMQConfiguration provideRabbitMQConfiguration(RabbitMQConfiguration commonRabbitMQConfiguration, OpenPaasConfiguration openPaasConfiguration) {
        return openPaasConfiguration.rabbitMqUri().toRabbitMqConfiguration(commonRabbitMQConfiguration);
    }

    @Provides
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public SimpleConnectionPool provideSimpleConnectionPool(@Named(OPENPAAS_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration) {
        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);
        try {
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
        } catch (Exception e) {
            LOGGER.info("Error while retrieving SimpleConnectionPool.Configuration, falling back to defaults.", e);
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
        }
    }

    @Provides
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public ReactorRabbitMQChannelPool provideReactorRabbitMQChannelPool(
        @Named(OPENPAAS_INJECTION_KEY) SimpleConnectionPool simpleConnectionPool,
        MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {

        ReactorRabbitMQChannelPool channelPool = new ReactorRabbitMQChannelPool(
            simpleConnectionPool.getResilientConnection(),
            ReactorRabbitMQChannelPool.Configuration.DEFAULT,
            metricFactory,
            gaugeRegistry);
        channelPool.start();
        return channelPool;
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideReconnectionHandler(OpenPaasContactsConsumer contactsConsumer) {
        return new OpenPaasContactsConsumerReconnectionHandler(contactsConsumer);
    }
}
