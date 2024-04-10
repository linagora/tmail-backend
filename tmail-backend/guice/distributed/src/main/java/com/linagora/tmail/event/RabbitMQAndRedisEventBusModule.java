package com.linagora.tmail.event;

import static com.linagora.tmail.event.DistributedEmailAddressContactEventModule.EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY;
import static org.apache.james.events.NamingStrategy.JMAP_NAMING_STRATEGY;

import java.io.FileNotFoundException;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.RabbitMQAndRedisEventBus;
import org.apache.james.events.RedisEventBusClientFactory;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.jmap.InjectionKeys;
import org.apache.james.jmap.change.Factory;
import org.apache.james.jmap.change.JmapEventSerializer;
import org.apache.james.jmap.pushsubscription.PushListener;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

import reactor.rabbitmq.Sender;

public class RabbitMQAndRedisEventBusModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQAndRedisEventBusModule.class);

    @Override
    protected void configure() {
        bind(EventBus.class).to(RabbitMQAndRedisEventBus.class);

        bind(RabbitMQAndRedisEventBus.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    private RedisConfiguration redisConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException, FileNotFoundException {
        try {
            return RedisConfiguration.from(propertiesProvider.getConfiguration("redis"));
        } catch (FileNotFoundException e) {
            LOGGER.error("Missing `redis.properties` configuration file for Redis Event Bus keys usage.");
            throw e;
        }
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.JMAP)
    RabbitMQAndRedisEventBus provideJmapEventBus(Sender sender, ReceiverProvider receiverProvider,
                                                 JmapEventSerializer eventSerializer,
                                                 RetryBackoffConfiguration retryBackoffConfiguration,
                                                 EventDeadLetters eventDeadLetters,
                                                 MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                                 @Named(InjectionKeys.JMAP) EventBusId eventBusId,
                                                 RabbitMQConfiguration configuration,
                                                 RedisEventBusClientFactory redisEventBusClientFactory) {
        return new RabbitMQAndRedisEventBus(
            JMAP_NAMING_STRATEGY,
            sender, receiverProvider, eventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration, redisEventBusClientFactory);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(RabbitMQAndRedisEventBus instance) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQAndRedisEventBus.class)
            .init(instance::start);
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.JMAP)
    EventBus provideJmapEventBus(@Named(InjectionKeys.JMAP) RabbitMQAndRedisEventBus rabbitMQAndRedisEventBus) {
        return rabbitMQAndRedisEventBus;
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(InjectionKeys.JMAP) RabbitMQAndRedisEventBus instance, PushListener pushListener) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQAndRedisEventBus.class)
            .init(() -> {
                instance.start();
                instance.register(pushListener);
            });
    }

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    RabbitMQAndRedisEventBus provideEmailAddressContactEventBus(Sender sender, ReceiverProvider receiverProvider,
                                                                TmailJmapEventSerializer eventSerializer,
                                                                RetryBackoffConfiguration retryBackoffConfiguration,
                                                                EventDeadLetters eventDeadLetters,
                                                                MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                                                @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventBusId eventBusId,
                                                                RabbitMQConfiguration configuration,
                                                                RedisEventBusClientFactory redisEventBusClientFactory) {
        return new RabbitMQAndRedisEventBus(
            EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY,
            sender, receiverProvider, eventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration, redisEventBusClientFactory);
    }

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    EventBus provideEmailAddressContactEventBus(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) RabbitMQAndRedisEventBus eventBus) {
        return eventBus;
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) RabbitMQAndRedisEventBus instance,
                                      EmailAddressContactListener emailAddressContactListener) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQAndRedisEventBus.class)
            .init(() -> {
                instance.start();
                instance.register(emailAddressContactListener);
            });
    }
}
