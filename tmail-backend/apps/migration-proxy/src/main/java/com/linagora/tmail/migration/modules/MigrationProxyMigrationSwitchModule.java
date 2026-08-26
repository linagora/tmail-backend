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

import static com.linagora.tmail.ScheduledReconnectionHandler.Module.QUEUES_TO_MONITOR_INJECT_KEY;

import java.util.Set;

import jakarta.inject.Singleton;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;
import com.linagora.tmail.migration.core.MigrationSwitchConsumer;
import com.linagora.tmail.migration.core.MigrationSwitchService;

/**
 * Starts the {@code migration:switch} AMQP consumer ({@link MigrationSwitchConsumer}). Only installed in
 * the clustered (RabbitMQ) event bus choice: it reuses the {@link ReactorRabbitMQChannelPool} that
 * {@code RabbitMQModule} contributes there rather than opening a dedicated connection.
 */
public class MigrationProxyMigrationSwitchModule extends AbstractModule {
    @Provides
    @Named(QUEUES_TO_MONITOR_INJECT_KEY)
    @Singleton
    Set<String> queuesToMonitor() {
        return Set.of(MigrationSwitchConsumer.QUEUE);
    }

    @Provides
    @Singleton
    MigrationSwitchConsumer provideMigrationSwitchConsumer(ReactorRabbitMQChannelPool channelPool, MigrationSwitchService migrationSwitchService) {
        return new MigrationSwitchConsumer(channelPool, migrationSwitchService);
    }

    @ProvidesIntoSet
    SimpleConnectionPool.ReconnectionHandler migrationSwitchConsumerReconnectionHandler(MigrationSwitchConsumer consumer) {
        return consumer;
    }

    @ProvidesIntoSet
    InitializationOperation startMigrationSwitchConsumer(MigrationSwitchConsumer consumer) {
        return InitilizationOperationBuilder
            .forClass(MigrationSwitchConsumer.class)
            .init(consumer::init);
    }
}
