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

package com.linagora.tmail;

import static com.rabbitmq.client.ConnectionFactory.DEFAULT_VHOST;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import javax.annotation.PreDestroy;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.util.DurationParser;
import org.apache.james.utils.InitializationOperation;
import org.apache.james.utils.InitilizationOperationBuilder;
import org.apache.james.utils.PropertiesProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.ProvidesIntoSet;
import com.google.inject.name.Named;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Singleton
public class ScheduledReconnectionHandler implements Startable {
    public record ScheduledReconnectionHandlerConfiguration(boolean enabled, Duration interval) {
        public static final boolean ENABLED = true;
        public static final Duration ONE_MINUTE = Duration.ofSeconds(60);

        public static ScheduledReconnectionHandlerConfiguration parse(PropertiesProvider propertiesProvider) throws ConfigurationException {
            try {
                Configuration configuration = propertiesProvider.getConfiguration("rabbitmq");
                boolean enabled = configuration.getBoolean("scheduled.consumer.reconnection.enabled", ENABLED);
                Duration interval = Optional.ofNullable(configuration.getString("scheduled.consumer.reconnection.interval", null))
                    .map(s -> DurationParser.parse(s, ChronoUnit.SECONDS))
                    .orElse(ONE_MINUTE);

                return new ScheduledReconnectionHandlerConfiguration(enabled, interval);
            } catch (FileNotFoundException e) {
                return new ScheduledReconnectionHandlerConfiguration(false, ONE_MINUTE);
            }
        }
    }

    public static class Module extends AbstractModule {
        public static final String EVENT_BUS_GROUP_QUEUES_TO_MONITOR_INJECT_KEY = "GROUP_QUEUES_TO_MONITOR";

        @Provides
        ScheduledReconnectionHandlerConfiguration configuration(PropertiesProvider propertiesProvider) throws ConfigurationException {
            return ScheduledReconnectionHandlerConfiguration.parse(propertiesProvider);
        }

        @ProvidesIntoSet
        InitializationOperation start(ScheduledReconnectionHandler scheduledReconnectionHandler) {
            return InitilizationOperationBuilder
                .forClass(ScheduledReconnectionHandler.class)
                .init(scheduledReconnectionHandler::start);
        }

        @Provides
        @Named(EVENT_BUS_GROUP_QUEUES_TO_MONITOR_INJECT_KEY)
        @Singleton
        Set<String> rabbitMQEventBusGroupQueuesToMonitor() {
            return ImmutableSet.of(
                "mailboxEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "jmapEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup",
                "contentDeletionEvent-workQueue-org.apache.james.events.GroupRegistrationHandler$GroupRegistrationHandlerGroup");
        }
    }

    private static final ImmutableList<String> STATIC_QUEUES_TO_MONITOR = new ImmutableList.Builder<String>()
        .add("JamesMailQueue-workqueue-spool",
        "JamesMailQueue-workqueue-outgoing",
        "sabre-contacts-queue-add",
        "sabre-contacts-queue-update",
        "sabre-contacts-queue-delete")
        .build();

    private static final Logger LOGGER = LoggerFactory.getLogger(ScheduledReconnectionHandler.class);
    
    private final Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers;
    private final RabbitMQManagementAPI mqManagementAPI;
    private final RabbitMQConfiguration configuration;
    private final SimpleConnectionPool connectionPool;
    private final ScheduledReconnectionHandlerConfiguration scheduledReconnectionHandlerConfiguration;
    private final ImmutableList<String> queuesToMonitor;
    private Disposable disposable;

    @Inject
    public ScheduledReconnectionHandler(Set<SimpleConnectionPool.ReconnectionHandler> reconnectionHandlers,
                                        RabbitMQConfiguration configuration,
                                        SimpleConnectionPool connectionPool,
                                        ScheduledReconnectionHandlerConfiguration scheduledReconnectionHandlerConfiguration,
                                        @Named(Module.EVENT_BUS_GROUP_QUEUES_TO_MONITOR_INJECT_KEY) Set<String> eventBusGroupQueuesToMonitor) {
        this.reconnectionHandlers = reconnectionHandlers;
        this.mqManagementAPI = RabbitMQManagementAPI.from(configuration);
        this.configuration = configuration;
        this.connectionPool = connectionPool;
        this.scheduledReconnectionHandlerConfiguration = scheduledReconnectionHandlerConfiguration;
        this.queuesToMonitor = ImmutableList.<String>builder()
            .addAll(STATIC_QUEUES_TO_MONITOR)
            .addAll(eventBusGroupQueuesToMonitor)
            .build();
    }

    @VisibleForTesting
    public ImmutableList<String> getQueuesToMonitor() {
        return queuesToMonitor;
    }

    public void start() {
        if (scheduledReconnectionHandlerConfiguration.enabled()) {
            disposable = Flux.interval(scheduledReconnectionHandlerConfiguration.interval())
                .filter(any -> restartNeeded())
                .concatMap(any -> restart())
                .onErrorResume(e -> {
                    LOGGER.warn("Failed to run scheduled RabbitMQ consumer checks", e);
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
        }
    }

    @PreDestroy
    public void stop() {
        Optional.ofNullable(disposable).ifPresent(Disposable::dispose);
    }
    
    private Mono<Void> restart() {
        LOGGER.warn("One of the queues has no consumer thus restarting all consumers");
        return connectionPool.getResilientConnection()
            .flatMap(connection -> Flux.fromIterable(reconnectionHandlers)
                .concatMap(h -> h.handleReconnection(connection))
                .then());
    }
    
    public boolean restartNeeded() {
        return queuesToMonitor.stream()
            .anyMatch(this::restartNeeded);
    }

    private boolean restartNeeded(String queue) {
        try {
            boolean hasConsumers = !mqManagementAPI.queueDetails(configuration.getVhost().orElse(DEFAULT_VHOST), queue)
                .getConsumerDetails()
                .isEmpty();

            if (!hasConsumers) {
                LOGGER.warn("The {} queue has no consumers", queue);
            }

            return !hasConsumers;
        } catch (RabbitMQManagementAPI.QueueNotFoundException e) {
            return false;
        }
    }
}
