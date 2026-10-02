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

package org.apache.james.events;

import java.util.List;

import org.apache.james.backends.rabbitmq.MonitoredRabbitMQConsumers;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Mono;

/**
 * The group consumers of every partition of an event bus, restarted with the event bus.
 */
public class PartitionedEventBusConsumers implements MonitoredRabbitMQConsumers {
    private final EventBus eventBus;
    private final SimpleConnectionPool connectionPool;
    private final String name;
    private final ImmutableList<RabbitMQEventBusConsumers> partitionConsumers;

    public PartitionedEventBusConsumers(EventBus eventBus, List<NamingStrategy> namingStrategies,
                                        SimpleConnectionPool connectionPool, Group groupRegistrationHandlerGroup) {
        Preconditions.checkArgument(!namingStrategies.isEmpty(), "At least one naming strategy is required");

        this.eventBus = eventBus;
        this.connectionPool = connectionPool;
        this.name = namingStrategies.getFirst().getEventBusName().value() + " event bus";
        this.partitionConsumers = namingStrategies.stream()
            .map(namingStrategy -> new RabbitMQEventBusConsumers(eventBus, namingStrategy, connectionPool, groupRegistrationHandlerGroup))
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public SimpleConnectionPool connectionPool() {
        return connectionPool;
    }

    @Override
    public List<String> queues() {
        return partitionConsumers.stream()
            .flatMap(consumers -> consumers.queues().stream())
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public Publisher<Void> restart(Connection connection) {
        return Mono.fromRunnable(eventBus::restart);
    }
}
