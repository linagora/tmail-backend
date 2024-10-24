package com.linagora.tmail;

import java.io.FileNotFoundException;

import jakarta.inject.Named;
import jakarta.inject.Provider;
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
import com.linagora.tmail.contact.OpenPaasContactsConsumer;

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
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public RabbitMQConfiguration provideRabbitMQConfiguration(OpenPaasConfiguration openPaasConfiguration, RabbitMQConfiguration fallbackRabbitMQConfiguration) {
        return openPaasConfiguration.maybeRabbitMqUri()
            .map(AmqpUri::toRabbitMqConfiguration)
            .orElse(fallbackRabbitMQConfiguration);
    }

    @Provides
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public SimpleConnectionPool provideSimpleConnectionPool(@Named(OPENPAAS_INJECTION_KEY) RabbitMQConfiguration rabbitMQConfiguration,
                                                            @Named(OPENPAAS_CONFIGURATION_NAME) Provider<Configuration> configuration) {
        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);
        try {
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.from(configuration.get()));
        } catch (Exception e) {
            LOGGER.info("Error while retrieving SimpleConnectionPool.Configuration, falling back to defaults.", e);
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
        }
    }

    @Provides
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public ReactorRabbitMQChannelPool provideReactorRabbitMQChannelPool(
        OpenPaasConfiguration openPaasConfiguration,
        @Named(OPENPAAS_INJECTION_KEY) SimpleConnectionPool simpleConnectionPool,
        ReactorRabbitMQChannelPool.Configuration configuration,
        MetricFactory metricFactory, GaugeRegistry gaugeRegistry) {

        ReactorRabbitMQChannelPool channelPool = new ReactorRabbitMQChannelPool(
            simpleConnectionPool.getResilientConnection(),
            configuration,
            metricFactory,
            gaugeRegistry);
        channelPool.start();
        return channelPool;
    }
}
