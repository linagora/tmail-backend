package com.linagora.tmail.contact;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.configuration2.Configuration;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.RabbitMQEmailAddressContactConfiguration;
import com.linagora.tmail.james.jmap.RabbitMQEmailAddressContactSubscriber;

import reactor.rabbitmq.RabbitFlux;
import reactor.rabbitmq.ReceiverOptions;
import reactor.rabbitmq.Sender;

public class RabbitMQEmailAddressContactModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQEmailAddressContactModule.class);
    public static final String RABBITMQ_CONTACT_CONFIGURATION_NAME = "rabbitmq";

    @Provides
    @Singleton
    public RabbitMQEmailAddressContactConfiguration rabbitMQEmailAddressContactConfiguration(@Named("rabbitmq") Configuration configuration) {
        return RabbitMQEmailAddressContactConfiguration.from(configuration);
    }

    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    @Provides
    @Singleton
    public RabbitMQConfiguration getMailQueueConfiguration(RabbitMQEmailAddressContactConfiguration rabbitMQEmailAddressContactConfiguration) {
        return RabbitMQConfiguration.builder()
            .amqpUri(rabbitMQEmailAddressContactConfiguration.getAmqpUri())
            .managementUri(rabbitMQEmailAddressContactConfiguration.getAmqpUri())
            .managementCredentials(rabbitMQEmailAddressContactConfiguration.getManagementCredentials())
            .build();
    }

    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    @Provides
    @Singleton
    public ReactorRabbitMQChannelPool provideReactorRabbitMQChannelPool(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) SimpleConnectionPool simpleConnectionPool,
                                                                        @Named(RABBITMQ_CONTACT_CONFIGURATION_NAME) Provider<Configuration> configuration,
                                                                        MetricFactory metricFactory) {
        ReactorRabbitMQChannelPool channelPool = new ReactorRabbitMQChannelPool(
            simpleConnectionPool.getResilientConnection(),
            provideChannelPoolConfiguration(configuration),
            metricFactory);
        channelPool.start();
        return channelPool;
    }

    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    @Provides
    @Singleton
    public Sender provideRabbitMQSender(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) ReactorRabbitMQChannelPool channelPool) {
        return channelPool.getSender();
    }

    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    @Provides
    @Singleton
    public ReceiverProvider provideRabbitMQReceiver(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) SimpleConnectionPool simpleConnectionPool) {
        return () -> RabbitFlux.createReceiver(new ReceiverOptions().connectionMono(simpleConnectionPool.getResilientConnection()));
    }

    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    @Provides
    @Singleton
    public SimpleConnectionPool simpleConnectionPool(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) RabbitMQConfiguration rabbitMQConfiguration,
                                                     @Named(RABBITMQ_CONTACT_CONFIGURATION_NAME) Provider<Configuration> configuration) {
        RabbitMQConnectionFactory rabbitMQConnectionFactory = new RabbitMQConnectionFactory(rabbitMQConfiguration);
        try {
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.from(configuration.get()));
        } catch (Exception e) {
            LOGGER.info("Error while retrieving SimpleConnectionPool.Configuration, falling back to defaults.", e);
            return new SimpleConnectionPool(rabbitMQConnectionFactory, SimpleConnectionPool.Configuration.DEFAULT);
        }
    }

    @ProvidesIntoSet
    public InitializationOperation contactSubscriber(RabbitMQEmailAddressContactSubscriber instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEmailAddressContactSubscriber.class)
            .init(instance::start);
    }

    private ReactorRabbitMQChannelPool.Configuration provideChannelPoolConfiguration(Provider<Configuration> configuration) {
        try {
            return ReactorRabbitMQChannelPool.Configuration.from(configuration.get());
        } catch (Exception e) {
            LOGGER.info("Error while retrieving ReactorRabbitMQChannelPool.Configuration, falling back to defaults.", e);
            return ReactorRabbitMQChannelPool.Configuration.DEFAULT;
        }
    }

}
