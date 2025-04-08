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

package com.linagora.tmail.contact;

import static com.linagora.tmail.OpenPaasModule.OPENPAAS_INJECTION_KEY;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.SimpleConnectionPool;
import org.apache.james.lifecycle.api.Startable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Connection;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Sender;

public class SabreContactsOperator implements Startable, SimpleConnectionPool.ReconnectionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(SabreContactsOperator.class);
    public static final String EXCHANGE_NAME_ADD = "sabre:contact:created";
    public static final String EXCHANGE_NAME_UPDATE = "sabre:contact:updated";
    public static final String EXCHANGE_NAME_DELETE = "sabre:contact:deleted";
    public static final String DEAD_LETTER_ADD = "sabre-contacts-queue-add-dead-letter";
    public static final String DEAD_LETTER_UPDATE = "sabre-contacts-queue-update-dead-letter";
    public static final String DEAD_LETTER_DELETE = "sabre-contacts-queue-delete-dead-letter";
    private static final boolean FALLBACK_CLASSIC_QUEUES_VERSION_1 = Boolean.parseBoolean(System.getProperty("fallback.classic.queues.v1", "false"));

    private final Sender sender;
    private final OpenPaasConfiguration openPaasConfiguration;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private final SabreContactsConsumer sabreContactsConsumer;

    @Inject
    public SabreContactsOperator(@Named(OPENPAAS_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                 @Named(OPENPAAS_INJECTION_KEY) RabbitMQConfiguration commonRabbitMQConfiguration,
                                 SabreContactsConsumer sabreContactsConsumer,
                                 OpenPaasConfiguration openPaasConfiguration) {
        this.sender = channelPool.getSender();
        this.openPaasConfiguration = openPaasConfiguration;
        this.commonRabbitMQConfiguration = commonRabbitMQConfiguration;
        this.sabreContactsConsumer = sabreContactsConsumer;
    }

    public void init() {
        // Declare the exchange and queue
        startExchange(EXCHANGE_NAME_ADD, SabreContactsConsumer.QUEUE_NAME_ADD, DEAD_LETTER_ADD);
        startExchange(EXCHANGE_NAME_UPDATE, SabreContactsConsumer.QUEUE_NAME_UPDATE, DEAD_LETTER_UPDATE);
        startExchange(EXCHANGE_NAME_DELETE, SabreContactsConsumer.QUEUE_NAME_DELETE, DEAD_LETTER_DELETE);

        sabreContactsConsumer.start();
    }

    public void startExchange(String exchange, String queue, String deadLetter) {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(exchange)
                    .durable(DURABLE).type(BuiltinExchangeType.FANOUT.getType())),
                sender.declareQueue(QueueSpecification
                    .queue(deadLetter)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier()
                        .build())),
                sender.declareQueue(QueueSpecification
                    .queue(queue)
                    .durable(DURABLE)
                    .arguments(queueArgumentSupplier()
                        .deadLetter(deadLetter)
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(exchange)
                    .queue(queue)
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();
    }

    private QueueArguments.Builder queueArgumentSupplier() {
        if (!openPaasConfiguration.contactConsumerConfiguration().get().quorumQueuesBypass()) {
            return commonRabbitMQConfiguration.workQueueArgumentsBuilder();
        }
        if (!FALLBACK_CLASSIC_QUEUES_VERSION_1) {
            return QueueArguments.builder()
                .classicQueueVersion(2);
        }
        return QueueArguments.builder();
    }

    @Override
    public Publisher<Void> handleReconnection(Connection connection) {
        return Mono.fromRunnable(sabreContactsConsumer::restart)
            .doOnError(error -> LOGGER.error("Error while handle reconnection for disconnector consumer", error))
            .then();
    }
}