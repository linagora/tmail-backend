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

package com.linagora.tmail.migration.modules;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.events.DefaultNamingStrategy;
import org.apache.james.events.EventBus;
import org.apache.james.events.EventBusId;
import org.apache.james.events.EventBusName;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.NamingStrategy;
import org.apache.james.events.RabbitMQEventBus;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.RoutingKeyConverter;
import org.apache.james.modules.queue.rabbitmq.RabbitMQModule;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.linagora.tmail.disconnector.DisconnectionRequestedEventSerializer;
import com.linagora.tmail.disconnector.DisconnectorNotificationRegistration;
import com.linagora.tmail.disconnector.DisconnectorRegistrationKey;

/**
 * Distributed (RabbitMQ) implementation of the {@code TMAIL_EVENT_BUS} that carries the disconnection
 * requests. This is the clustered option of the event bus module chooser: a disconnection request
 * published on any node is fanned out over RabbitMQ so the node actually holding the user's proxied IMAP
 * connection is the one that closes it, wherever the migration was triggered. This is what makes running
 * several migration proxies behind a load balancer (HA) actually force-reconnect migrated users.
 *
 * <p>Only the disconnection plumbing rides this bus, so we keep the wiring minimal: the RabbitMQ backend
 * (connection pool, configuration) comes from {@link RabbitMQModule}, and the bus is fed with the single
 * {@link DisconnectionRequestedEventSerializer} / {@link DisconnectorRegistrationKey} pair rather than the
 * full mailbox event stack the other Twake Mail apps carry.
 */
public class MigrationProxyRabbitMQEventBusModule extends AbstractModule {
    private static final NamingStrategy NAMING_STRATEGY = new DefaultNamingStrategy(new EventBusName("migrationProxyDisconnect"));

    @Override
    protected void configure() {
        install(new RabbitMQModule());
        bind(EventDeadLetters.class).to(MemoryEventDeadLetters.class);
    }

    @Provides
    @Singleton
    @Named("TMAIL_EVENT_BUS")
    RabbitMQEventBus provideTmailEventBus(RabbitMQEventBus.Factory eventBusFactory, RabbitMQConfiguration rabbitMQConfiguration) {
        return eventBusFactory.create(EventBusId.random(),
            NAMING_STRATEGY,
            new RoutingKeyConverter(ImmutableSet.of(new DisconnectorRegistrationKey.Factory())),
            new DisconnectionRequestedEventSerializer(),
            new RabbitMQEventBus.Configurations(rabbitMQConfiguration, RetryBackoffConfiguration.DEFAULT, EventBus.Configuration.DEFAULT));
    }

    @Provides
    @Singleton
    @Named("TMAIL_EVENT_BUS")
    EventBus provideTmailEventBus(@Named("TMAIL_EVENT_BUS") RabbitMQEventBus eventBus) {
        return eventBus;
    }

    @ProvidesIntoSet
    InitializationOperation startEventBus(@Named("TMAIL_EVENT_BUS") RabbitMQEventBus eventBus,
                                          DisconnectorNotificationRegistration registration) {
        return InitilizationOperationBuilder
            .forClass(RabbitMQEventBus.class)
            .init(() -> {
                // Start the bus (declares the RabbitMQ exchange/queues) before registering the listener:
                // registration.register() subscribes the DisconnectionEventListener on the running bus.
                eventBus.start();
                registration.register();
            });
    }
}
