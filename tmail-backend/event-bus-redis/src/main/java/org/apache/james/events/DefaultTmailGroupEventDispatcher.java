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

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.evaluateAutoDelete;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;
import static org.apache.james.backends.rabbitmq.Constants.evaluateExclusive;
import static org.apache.james.events.RabbitMQAndRedisEventBus.EVENT_BUS_ID;

import java.time.Duration;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.events.RoutingKeyConverter.RoutingKey;

import com.google.common.collect.ImmutableMap;
import com.rabbitmq.client.AMQP;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

class DefaultTmailGroupEventDispatcher {
    private final NamingStrategy namingStrategy;
    private final Sender sender;
    private final AMQP.BasicProperties basicProperties;
    private final RabbitMQConfiguration configuration;

    DefaultTmailGroupEventDispatcher(NamingStrategy namingStrategy, EventBusId eventBusId, Sender sender,
                                     RabbitMQConfiguration configuration) {
        this.namingStrategy = namingStrategy;
        this.sender = sender;
        this.basicProperties = new AMQP.BasicProperties.Builder()
            .headers(ImmutableMap.of(EVENT_BUS_ID, eventBusId.asString()))
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .priority(PERSISTENT_TEXT_PLAIN.getPriority())
            .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
            .build();
        this.configuration = configuration;
    }

    Mono<Void> start() {
        return Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(namingStrategy.exchange())
                    .durable(true)
                    .type(DIRECT_EXCHANGE)),
                sender.declareExchange(ExchangeSpecification.exchange(namingStrategy.deadLetterExchange())
                    .durable(DURABLE)
                    .type(DIRECT_EXCHANGE)),
                sender.declareQueue(namingStrategy.deadLetterQueue()
                    .durable(evaluateDurable(DURABLE, configuration.isQuorumQueuesUsed()))
                    .exclusive(evaluateExclusive(!EXCLUSIVE, configuration.isQuorumQueuesUsed()))
                    .autoDelete(evaluateAutoDelete(!AUTO_DELETE, configuration.isQuorumQueuesUsed()))
                    .arguments(configuration.workQueueArgumentsBuilder()
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(namingStrategy.deadLetterExchange())
                    .queue(namingStrategy.deadLetterQueue().getName())
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then();
    }

    Mono<Void> dispatch(byte[] serializedEvent) {
        if (configuration.isEventBusPublishConfirmEnabled()) {
            return Mono.from(sender.sendWithPublishConfirms(Mono.just(toMessage(serializedEvent, RoutingKey.empty())))
                .subscribeOn(Schedulers.boundedElastic()))
                .filter(outboundMessageResult -> !outboundMessageResult.isAck())
                .handle((result, sink) -> sink.error(new Exception("Publish was not acked")))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .then();
        }

        return sender.send(Mono.just(toMessage(serializedEvent, RoutingKey.empty())));
    }

    private OutboundMessage toMessage(byte[] serializedEvent, RoutingKey routingKey) {
        return new OutboundMessage(namingStrategy.exchange(), routingKey.asString(), basicProperties, serializedEvent);
    }
}
