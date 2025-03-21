/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.event;

import static com.linagora.tmail.ScheduledReconnectionHandler.Module.EVENT_BUS_GROUP_QUEUES_TO_MONITOR_INJECT_KEY;
import static com.linagora.tmail.event.DistributedEmailAddressContactEventModule.EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY;
import static org.apache.james.events.NamingStrategy.JMAP_NAMING_STRATEGY;
import static org.apache.james.events.NamingStrategy.MAILBOX_EVENT_NAMING_STRATEGY;

import java.io.FileNotFoundException;
import java.util.Set;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.events.CleanRedisEventBusService;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusReconnectionHandler;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitEventBusConsumerHealthCheck;
import org.apache.james.events.RabbitMQAndRedisEventBus;
import org.apache.james.events.RabbitMQJmapEventBusDeadLetterQueueHealthCheck;
import org.apache.james.events.RabbitMQMailboxEventBusDeadLetterQueueHealthCheck;
import org.apache.james.events.RedisEventBusClientFactory;
import org.apache.james.events.RedisEventBusConfiguration;
import org.apache.james.events.RegistrationKey;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.events.TmailGroupRegistrationHandler;
import org.apache.james.jmap.InjectionKeys;
import org.apache.james.jmap.change.Factory;
import org.apache.james.jmap.change.JmapEventSerializer;
import org.apache.james.jmap.pushsubscription.PushListener;
import org.apache.james.mailbox.events.MailboxIdRegistrationKey;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.ReactorUtils;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Names;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;
import com.linagora.tmail.james.jmap.contact.TmailJmapEventSerializer;

import reactor.rabbitmq.Sender;

public class RabbitMQAndRedisEventBusModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQAndRedisEventBusModule.class);

    @Override
    protected void configure() {
        bind(NamingStrategy.class).toInstance(MAILBOX_EVENT_NAMING_STRATEGY);

        Multibinder.newSetBinder(binder(), RegistrationKey.Factory.class)
            .addBinding().to(MailboxIdRegistrationKey.Factory.class);

        bind(RetryBackoffConfiguration.class).toInstance(RetryBackoffConfiguration.DEFAULT);
        bind(EventBusId.class).toInstance(EventBusId.random());

        Multibinder.newSetBinder(binder(), HealthCheck.class)
            .addBinding().to(RabbitMQMailboxEventBusDeadLetterQueueHealthCheck.class);

        bind(EventBus.class).to(RabbitMQAndRedisEventBus.class);

        bind(RabbitMQAndRedisEventBus.class).in(Scopes.SINGLETON);

        bind(EventBusId.class).annotatedWith(Names.named(InjectionKeys.JMAP)).toInstance(EventBusId.random());
    }

    @ProvidesIntoSet
    EventBus registerMailboxEventBusToDeadLetterRedeliverService(EventBus eventBus) {
        return eventBus;
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(RabbitMQAndRedisEventBus instance,
                                      RedisEventBusClientFactory redisEventBusClientFactory,
                                      RoutingKeyConverter routingKeyConverter) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQAndRedisEventBus.class)
            .init(() -> {
                instance.start();
                new CleanRedisEventBusService(redisEventBusClientFactory, routingKeyConverter).cleanUp()
                    .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
                    .subscribe();
            });
    }

    @ProvidesIntoSet
    HealthCheck healthCheck(RabbitMQAndRedisEventBus eventBus, NamingStrategy namingStrategy,
                            SimpleConnectionPool connectionPool) {
        return new RabbitEventBusConsumerHealthCheck(eventBus, namingStrategy, connectionPool,
            TmailGroupRegistrationHandler.GROUP);
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideReconnectionHandler(RabbitMQAndRedisEventBus eventBus) {
        return new EventBusReconnectionHandler(eventBus);
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
                                                 RedisEventBusClientFactory redisEventBusClientFactory,
                                                 RedisEventBusConfiguration redisEventBusConfiguration) {
        return new RabbitMQAndRedisEventBus(
            JMAP_NAMING_STRATEGY,
            sender, receiverProvider, eventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration, redisEventBusClientFactory, redisEventBusConfiguration);
    }

    @Provides
    @Singleton
    @Named(InjectionKeys.JMAP)
    EventBus provideJmapEventBus(@Named(InjectionKeys.JMAP) RabbitMQAndRedisEventBus rabbitMQAndRedisEventBus) {
        return rabbitMQAndRedisEventBus;
    }

    @ProvidesIntoSet
    EventBus registerJmapEventBusToDeadLettersRedeliverService(@Named(InjectionKeys.JMAP) EventBus eventBus) {
        return eventBus;
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

    @ProvidesIntoSet
    EventSerializer registerJmapEventSerializers(JmapEventSerializer jmapEventSerializer) {
        return jmapEventSerializer;
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideJMAPReconnectionHandler(@Named(InjectionKeys.JMAP) RabbitMQAndRedisEventBus eventBus) {
        return new EventBusReconnectionHandler(eventBus);
    }

    @ProvidesIntoSet
    HealthCheck healthCheck(@Named(InjectionKeys.JMAP) RabbitMQAndRedisEventBus eventBus,
                            SimpleConnectionPool connectionPool) {
        return new RabbitEventBusConsumerHealthCheck(eventBus, JMAP_NAMING_STRATEGY, connectionPool,
            TmailGroupRegistrationHandler.GROUP);
    }

    @ProvidesIntoSet
    HealthCheck jmapEventBusDeadLetterQueueHealthCheck(RabbitMQConfiguration rabbitMQConfiguration) {
        return new RabbitMQJmapEventBusDeadLetterQueueHealthCheck(rabbitMQConfiguration);
    }

    @Provides
    @Singleton
    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE)
    RabbitMQAndRedisEventBus provideEmailAddressContactEventBus(Sender sender, ReceiverProvider receiverProvider,
                                                                TmailJmapEventSerializer eventSerializer,
                                                                RetryBackoffConfiguration retryBackoffConfiguration,
                                                                @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventDeadLetters eventDeadLetters,
                                                                MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                                                @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventBusId eventBusId,
                                                                RabbitMQConfiguration configuration,
                                                                RedisEventBusClientFactory redisEventBusClientFactory,
                                                                RedisEventBusConfiguration redisEventBusConfiguration) {
        return new RabbitMQAndRedisEventBus(
            EMAIL_ADDRESS_CONTACT_NAMING_STRATEGY,
            sender, receiverProvider, eventSerializer, retryBackoffConfiguration, new RoutingKeyConverter(ImmutableSet.of(new Factory())),
            eventDeadLetters, metricFactory, channelPool, eventBusId, configuration, redisEventBusClientFactory, redisEventBusConfiguration);
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

    @Provides
    @Singleton
    RedisEventBusConfiguration redisEventBusConfiguration(PropertiesProvider propertiesProvider) throws ConfigurationException {
        try {
            Configuration config = propertiesProvider.getConfiguration("redis");
            return RedisEventBusConfiguration.from(config);
        } catch (FileNotFoundException e) {
            LOGGER.info("Missing `redis.properties` configuration file -> using default RedisEventBusConfiguration");
            return RedisEventBusConfiguration.DEFAULT;
        }
    }

    @Provides
    @Named(EVENT_BUS_GROUP_QUEUES_TO_MONITOR_INJECT_KEY)
    @Singleton
    Set<String> redisEventBusGroupQueuesToMonitor() {
        return ImmutableSet.of(
            "mailboxEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
            "jmapEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
    }
}
