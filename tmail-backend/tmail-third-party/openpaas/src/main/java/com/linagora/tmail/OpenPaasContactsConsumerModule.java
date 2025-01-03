package com.linagora.tmail;

import static com.linagora.tmail.OpenPaasModule.OPENPAAS_INJECTION_KEY;

import java.util.List;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.RabbitMQConnectionFactory;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.metrics.api.GaugeRegistry;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.Host;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.contact.OpenPaasContactsConsumer;
import com.linagora.tmail.contact.OpenPaasContactsConsumerReconnectionHandler;

public class OpenPaasContactsConsumerModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasContactsConsumerModule.class);

    @ProvidesIntoSet
    public InitializationOperation initializeContactsConsumer(OpenPaasContactsConsumer instance) {
        return InitilizationOperationBuilder
            .forClass(OpenPaasContactsConsumer.class)
            .init(instance::start);
    }

    @Provides
    @Named(OPENPAAS_INJECTION_KEY)
    @Singleton
    public RabbitMQConfiguration provideRabbitMQConfiguration(RabbitMQConfiguration commonRabbitMQConfiguration,
                                                              OpenPaasConfiguration openPaasConfiguration) {
        Preconditions.checkArgument(openPaasConfiguration.contactConsumerConfiguration().isPresent(),
            "OpenPaasConfiguration should have a contact consumer configuration");
        List<AmqpUri> uris = openPaasConfiguration.contactConsumerConfiguration().get().amqpUri();
        return uris.getFirst()
            .toRabbitMqConfiguration(commonRabbitMQConfiguration)
            .hosts(uris.stream().map(uri -> Host.from(uri.getUri().getHost(), uri.getPort())).collect(ImmutableList.toImmutableList()))
            .build();
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
