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

import static com.linagora.tmail.RabbitMQDisconnectorConsumer.TMAIL_DISCONNECTOR_QUEUE_NAME;
import static com.linagora.tmail.RabbitMQDisconnectorNotifier.TMAIL_DISCONNECTOR_EXCHANGE_NAME;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.lifecycle.api.Startable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Sender;

public class RabbitMQDisconnectorOperator implements Startable, SimpleConnectionPool.ReconnectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQDisconnectorOperator.class);
    private final Sender sender;
    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final RabbitMQDisconnectorConsumer disconnectorConsumer;

    @Inject
    public RabbitMQDisconnectorOperator(Sender sender,
                                        RabbitMQConfiguration rabbitMQConfiguration,
                                        RabbitMQDisconnectorConsumer disconnectorConsumer) {
        this.sender = sender;
        this.rabbitMQConfiguration = rabbitMQConfiguration;
        this.disconnectorConsumer = disconnectorConsumer;
    }

    public void init() {
        // Declare the exchange and queue
        Flux.concat(sender.declareExchange(ExchangeSpecification.exchange(TMAIL_DISCONNECTOR_EXCHANGE_NAME)
                    .type(BuiltinExchangeType.FANOUT.getType())
                    .durable(DURABLE)),
                sender.declareQueue(QueueSpecification
                    .queue(TMAIL_DISCONNECTOR_QUEUE_NAME)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier().build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(TMAIL_DISCONNECTOR_EXCHANGE_NAME)
                    .queue(TMAIL_DISCONNECTOR_QUEUE_NAME)
                    .routingKey(RabbitMQDisconnectorNotifier.ROUTING_KEY)))
            .then().block();

        // Start the consumer
        disconnectorConsumer.start();
    }

    private QueueArguments.Builder queueArgumentSupplier() {
        QueueArguments.Builder queueArgumentBuilder = QueueArguments.builder().quorumQueue()
            .replicationFactor(rabbitMQConfiguration.getQuorumQueueReplicationFactor());
        rabbitMQConfiguration.getQuorumQueueDeliveryLimit().ifPresent(queueArgumentBuilder::deliveryLimit);
        rabbitMQConfiguration.getQueueTTL().ifPresent(queueArgumentBuilder::queueTTL);
        return queueArgumentBuilder;
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(disconnectorConsumer::restart)
            .doOnError(error -> LOGGER.error("Error while handle reconnection for disconnector consumer", error))
            .then();
    }
}
