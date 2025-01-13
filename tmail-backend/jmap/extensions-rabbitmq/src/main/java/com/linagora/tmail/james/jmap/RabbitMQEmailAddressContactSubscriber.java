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

package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.REQUEUE;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;
import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.james.jmap.contact.ContactMessageHandlerResult;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessageHandler;
import com.linagora.tmail.james.jmap.contact.Failure;
import com.linagora.tmail.james.jmap.json.EmailAddressContactMessageSerializer;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

public class RabbitMQEmailAddressContactSubscriber implements Startable, Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQEmailAddressContactSubscriber.class);

    private final RabbitMQEmailAddressContactConfiguration rabbitMQEmailAddressContactConfiguration;
    private final ReceiverProvider receiverProvider;
    private final EmailAddressContactMessageHandler messageHandler;
    private final Sender sender;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private Disposable messageConsume;

    @Inject
    public RabbitMQEmailAddressContactSubscriber(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) ReceiverProvider receiverProvider,
                                                 @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) Sender sender,
                                                 RabbitMQEmailAddressContactConfiguration configuration,
                                                 EmailAddressContactMessageHandler messageHandler,
                                                 RabbitMQConfiguration commonRabbitMQConfiguration) {
        this.rabbitMQEmailAddressContactConfiguration = configuration;
        this.receiverProvider = receiverProvider;
        this.messageHandler = messageHandler;
        this.sender = sender;
        this.commonRabbitMQConfiguration = commonRabbitMQConfiguration;
    }

    public void start() {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(rabbitMQEmailAddressContactConfiguration.getExchangeName())
                    .durable(DURABLE)),
                sender.declareExchange(ExchangeSpecification.exchange(rabbitMQEmailAddressContactConfiguration.getDeadLetterExchange())
                    .durable(DURABLE)),
                sender.declareQueue(QueueSpecification
                    .queue(rabbitMQEmailAddressContactConfiguration.queueName())
                    .durable(evaluateDurable(DURABLE, commonRabbitMQConfiguration.isQuorumQueuesUsed()))
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder()
                        .put("x-dead-letter-exchange", rabbitMQEmailAddressContactConfiguration.getDeadLetterExchange())
                        .put("x-dead-letter-routing-key", EMPTY_ROUTING_KEY)
                        .build())),
                sender.declareQueue(QueueSpecification
                    .queue(rabbitMQEmailAddressContactConfiguration.getDeadLetterQueue())
                    .durable(evaluateDurable(DURABLE, commonRabbitMQConfiguration.isQuorumQueuesUsed()))
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder()
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(rabbitMQEmailAddressContactConfiguration.getExchangeName())
                    .queue(rabbitMQEmailAddressContactConfiguration.queueName())
                    .routingKey(EMPTY_ROUTING_KEY)),
                sender.bind(BindingSpecification.binding()
                    .exchange(rabbitMQEmailAddressContactConfiguration.getDeadLetterExchange())
                    .queue(rabbitMQEmailAddressContactConfiguration.getDeadLetterQueue())
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();

        messageConsume = messagesConsume();
    }

    public Flux<AcknowledgableDelivery> delivery() {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(rabbitMQEmailAddressContactConfiguration.queueName()),
            Receiver::close);
    }

    private Disposable messagesConsume() {
        return delivery()
            .flatMap(delivery -> messageConsume(delivery, new String(delivery.getBody(), StandardCharsets.UTF_8)), DEFAULT_CONCURRENCY)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();
    }

    private Mono<ContactMessageHandlerResult> messageConsume(AcknowledgableDelivery ackDelivery, String messagePayload) {
        return Mono.just(messagePayload)
            .map(EmailAddressContactMessageSerializer::deserializeEmailAddressContactMessageAsJava)
            .flatMap(message -> Mono.from(messageHandler.handler(message)))
            .flatMap(handlerResult -> {
                if (handlerResult instanceof Failure failure) {
                    return Mono.error(failure.error());
                }
                ackDelivery.ack();
                return Mono.just(handlerResult);
            })
            .onErrorResume(error -> {
                LOGGER.error("Error when consume message '{}'", messagePayload, error);
                ackDelivery.nack(!REQUEUE);
                return Mono.empty();
            });
    }

    @Override
    public void close() {
        Optional.ofNullable(messageConsume).ifPresent(Disposable::dispose);
    }
}
