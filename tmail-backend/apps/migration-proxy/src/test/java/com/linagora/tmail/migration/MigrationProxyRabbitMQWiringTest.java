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

package com.linagora.tmail.migration;

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.DEFAULT_MANAGEMENT_CREDENTIAL;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.GuiceJamesServer;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.rabbitmq.DockerRabbitMQ;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.server.core.configuration.Configuration;
import org.apache.james.utils.GuiceProbe;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.Multibinder;
import com.linagora.tmail.ScheduledReconnectionHandler;
import com.linagora.tmail.migration.MigrationProxyServer.EventBusModuleChoice;
import com.linagora.tmail.migration.core.MigrationSwitchConsumer;

/**
 * Boots the migration proxy against a real RabbitMQ with the distributed (HA) event bus injected, to
 * prove that the clustered disconnection wiring ({@code MigrationProxyRabbitMQEventBusModule}) assembles
 * and the server actually starts. The RabbitMQ mode is injected through
 * {@link MigrationProxyServer#createServer(Configuration, EventBusModuleChoice)} rather than dropped as a
 * {@code rabbitmq.properties} file, which is the point of resolving the choice from the configuration.
 */
class MigrationProxyRabbitMQWiringTest {
    static class ReconnectionHandlersProbe implements GuiceProbe {
        private final Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers;
        private final MigrationSwitchConsumer migrationSwitchConsumer;
        private final ScheduledReconnectionHandler scheduledReconnectionHandler;

        @Inject
        ReconnectionHandlersProbe(Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers,
                                  MigrationSwitchConsumer migrationSwitchConsumer,
                                  ScheduledReconnectionHandler scheduledReconnectionHandler) {
            this.reconnectionHandlers = reconnectionHandlers;
            this.migrationSwitchConsumer = migrationSwitchConsumer;
            this.scheduledReconnectionHandler = scheduledReconnectionHandler;
        }

        boolean containsMigrationSwitchConsumer() {
            return reconnectionHandlers.contains(migrationSwitchConsumer);
        }

        List<String> queuesToMonitor() {
            return scheduledReconnectionHandler.getQueuesToMonitor();
        }
    }

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.empty();

    static DockerRabbitMQ rabbitMQ = DockerRabbitMQ.withoutCookie();

    @TempDir
    static File workingDirectory;

    private GuiceJamesServer proxy;

    @BeforeAll
    static void setUpAll() {
        rabbitMQ.start();
    }

    @AfterAll
    static void tearDownAll() {
        rabbitMQ.stop();
    }

    @AfterEach
    void tearDown() {
        if (proxy != null) {
            proxy.stop();
        }
    }

    @Test
    void serverShouldStartWithRabbitMQEventBus() throws Exception {
        Configuration configuration = Configuration.builder()
            .workingDirectory(workingDirectory)
            .configurationFromClasspath()
            .build();

        proxy = MigrationProxyServer.createServer(configuration, EventBusModuleChoice.RABBITMQ)
            .overrideWith(postgresExtension.getModule())
            .overrideWith(new RabbitMQConfigurationModule())
            .overrideWith(binder -> Multibinder.newSetBinder(binder, GuiceProbe.class)
                .addBinding().to(ReconnectionHandlersProbe.class));
        proxy.start();

        assertThat(proxy.isStarted()).isTrue();

        // reconnection on migration switch queue's consumer
        assertThat(proxy.getProbe(ReconnectionHandlersProbe.class).containsMigrationSwitchConsumer()).isTrue();
        assertThat(proxy.getProbe(ReconnectionHandlersProbe.class).queuesToMonitor())
            .containsExactly(MigrationSwitchConsumer.QUEUE);
    }

    private static class RabbitMQConfigurationModule extends AbstractModule {
        @Provides
        @Singleton
        RabbitMQConfiguration provideRabbitMQConfiguration() throws Exception {
            return RabbitMQConfiguration.builder()
                .amqpUri(rabbitMQ.amqpUri())
                .managementUri(rabbitMQ.managementUri())
                .managementCredentials(DEFAULT_MANAGEMENT_CREDENTIAL)
                .build();
        }
    }
}
