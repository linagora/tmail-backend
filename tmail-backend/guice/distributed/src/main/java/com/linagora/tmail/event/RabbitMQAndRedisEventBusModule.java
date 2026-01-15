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
import static org.apache.james.events.NamingStrategy.CONTENT_DELETION_NAMING_STRATEGY;
import static org.apache.james.events.NamingStrategy.JMAP_NAMING_STRATEGY;
import static org.apache.james.events.NamingStrategy.MAILBOX_EVENT_NAMING_STRATEGY;
import static org.apache.james.modules.event.ContentDeletionEventBusModule.CONTENT_DELETION;

import java.io.FileNotFoundException;
import java.util.Set;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.core.healthcheck.HealthCheck;
import org.apache.james.event.json.MailboxEventSerializer;
import org.apache.james.events.CleanRedisEventBusService;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusReconnectionHandler;
import org.apache.james.events.EventListener;
import org.apache.james.events.EventSerializer;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitEventBusConsumerHealthCheck;
import org.apache.james.events.RabbitMQAndRedisEventBus;
import org.apache.james.events.RabbitMQContentDeletionEventBusDeadLetterQueueHealthCheck;
import org.apache.james.events.RabbitMQEventBus;
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
import com.linagora.tmail.event.EmailAddressContactRabbitMQEventBusModule.EmailAddressContactEventLoader;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactListener;

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

        bind(EventBusId.class).annotatedWith(Names.named(CONTENT_DELETION)).toInstance(EventBusId.random());
        Multibinder.newSetBinder(binder(), EventListener.ReactiveGroupEventListener.class, Names.named(CONTENT_DELETION));
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
    RabbitMQAndRedisEventBus provideJmapEventBus(RabbitMQAndRedisEventBus.Factory eventBusFactory,
                                                 JmapEventSerializer eventSerializer,
                                                 @Named(InjectionKeys.JMAP) EventBusId eventBusId,
                                                 RabbitMQEventBus.Configurations configurations) {
        return eventBusFactory.create(eventBusId, JMAP_NAMING_STRATEGY, new RoutingKeyConverter(ImmutableSet.of(new Factory())), eventSerializer, configurations);
    }

    @Provides
    @Singleton
        RabbitMQEventBus.Configurations provideRabbitMQEventBusConfigurations(RetryBackoffConfiguration retryBackoffConfiguration, RabbitMQConfiguration configuration, EventBus.Configuration eventBusConfiguration) {
        return new RabbitMQEventBus.Configurations(configuration, retryBackoffConfiguration, eventBusConfiguration);
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

    @ProvidesIntoSet
    public InitializationOperation registerListener(
        @Named(TmailEventModule.TMAIL_EVENT_BUS_INJECT_NAME) EventBus eventBus,
        EmailAddressContactListener emailAddressContactListener) {
        return InitilizationOperationBuilder
            .forClass(EmailAddressContactEventLoader.class)
            .init(() -> eventBus.register(emailAddressContactListener));
    }

    @Provides
    @Singleton
    @Named(CONTENT_DELETION)
    RabbitMQAndRedisEventBus provideContentDeletionEventBus(RabbitMQAndRedisEventBus.Factory eventBusFactory,
                                                            MailboxEventSerializer eventSerializer,
                                                            @Named(CONTENT_DELETION) EventBusId eventBusId,
                                                            RabbitMQEventBus.Configurations configurations) {
        return eventBusFactory.create(eventBusId, CONTENT_DELETION_NAMING_STRATEGY, new RoutingKeyConverter(ImmutableSet.of(new Factory())), eventSerializer, configurations);
    }

    @Provides
    @Singleton
    @Named(CONTENT_DELETION)
    EventBus provideContentDeletionEventBus(@Named(CONTENT_DELETION) RabbitMQAndRedisEventBus eventBus) {
        return eventBus;
    }

    @ProvidesIntoSet
    EventBus registerContentDeletionEventBusToDeadLettersRedeliverService(@Named(CONTENT_DELETION) EventBus eventBus) {
        return eventBus;
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler provideContentDeletionReconnectionHandler(@Named(CONTENT_DELETION) RabbitMQAndRedisEventBus eventBus) {
        return new EventBusReconnectionHandler(eventBus);
    }

    @ProvidesIntoSet
    HealthCheck provideContentDeletionConsumerHealthCheck(@Named(CONTENT_DELETION) RabbitMQAndRedisEventBus eventBus,
                                                          SimpleConnectionPool connectionPool) {
        return new RabbitEventBusConsumerHealthCheck(eventBus, CONTENT_DELETION_NAMING_STRATEGY, connectionPool,
            TmailGroupRegistrationHandler.GROUP);
    }

    @ProvidesIntoSet
    HealthCheck provideContentDeletionEventBusDeadLetterQueueHealthCheck(RabbitMQConfiguration rabbitMQConfiguration) {
        return new RabbitMQContentDeletionEventBusDeadLetterQueueHealthCheck(rabbitMQConfiguration);
    }

    @ProvidesIntoSet
    InitializationOperation workQueue(@Named(CONTENT_DELETION) RabbitMQAndRedisEventBus instance,
                                      @Named(CONTENT_DELETION) Set<EventListener.ReactiveGroupEventListener> contentDeletionListeners) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQAndRedisEventBus.class)
            .init(() -> {
                instance.start();
                contentDeletionListeners.forEach(instance::register);
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
            "jmapEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup",
            "contentDeletionEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
    }
}
