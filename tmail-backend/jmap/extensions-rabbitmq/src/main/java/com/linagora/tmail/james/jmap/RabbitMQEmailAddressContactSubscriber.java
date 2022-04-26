package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.REQUEUE;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
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
    static class RabbitMQConfiguration {
        static final String DEAD_LETTER_EXCHANGE_PREFIX = "TmailQueue-dead-letter-exchange-";
        static final String DEAD_LETTER_QUEUE_PREFIX = "TmailQueue-dead-letter-queue-";

        public static RabbitMQConfiguration from(Configuration config) {
            return new RabbitMQConfiguration(
                config.getString("address.contact.exchange", "AddressContactExchangeDefault"),
                config.getString("address.contact.queue", "AddressContactQueueDefault"));
        }

        private final String exchangeName;
        private final String queueName;

        public RabbitMQConfiguration(String exchangeName, String queueName) {
            Preconditions.checkArgument(StringUtils.isNotBlank(exchangeName));
            Preconditions.checkArgument(StringUtils.isNotBlank(queueName));
            this.exchangeName = exchangeName;
            this.queueName = queueName;
        }

        public String getExchangeName() {
            return exchangeName;
        }

        public String getQueueName() {
            return queueName;
        }

        public String getDeadLetterExchange() {
            return DEAD_LETTER_EXCHANGE_PREFIX + getQueueName();
        }

        public String getDeadLetterQueue() {
            return DEAD_LETTER_QUEUE_PREFIX + getQueueName();
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQEmailAddressContactSubscriber.class);

    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final ReceiverProvider receiverProvider;
    private final EmailAddressContactMessageHandler messageHandler;
    private final Sender sender;
    private Disposable messageConsume;

    @Inject
    public RabbitMQEmailAddressContactSubscriber(ReceiverProvider receiverProvider,
                                                 Sender sender,
                                                 EmailAddressContactMessageHandler messageHandler,
                                                 Configuration configuration) {
        this.rabbitMQConfiguration = RabbitMQConfiguration.from(configuration);
        this.receiverProvider = receiverProvider;
        this.messageHandler = messageHandler;
        this.sender = sender;
    }

    public void start() {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(rabbitMQConfiguration.getExchangeName())
                    .durable(DURABLE)),
                sender.declareExchange(ExchangeSpecification.exchange(rabbitMQConfiguration.getDeadLetterExchange())
                    .durable(DURABLE)),
                sender.declareQueue(QueueSpecification
                    .queue(rabbitMQConfiguration.getQueueName())
                    .durable(DURABLE)
                    .arguments(ImmutableMap.<String, Object>builder()
                        .put("x-dead-letter-exchange", rabbitMQConfiguration.getDeadLetterExchange())
                        .put("x-dead-letter-routing-key", EMPTY_ROUTING_KEY)
                        .build())),
                sender.declareQueue(QueueSpecification
                    .queue(rabbitMQConfiguration.getDeadLetterQueue())
                    .durable(DURABLE)),
                sender.bind(BindingSpecification.binding()
                    .exchange(rabbitMQConfiguration.getExchangeName())
                    .queue(rabbitMQConfiguration.getQueueName())
                    .routingKey(EMPTY_ROUTING_KEY)),
                sender.bind(BindingSpecification.binding()
                    .exchange(rabbitMQConfiguration.getDeadLetterExchange())
                    .queue(rabbitMQConfiguration.getDeadLetterQueue())
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();

        messageConsume = messagesConsume();
    }

    public Flux<AcknowledgableDelivery> delivery() {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(rabbitMQConfiguration.getQueueName()),
            Receiver::close);
    }

    private Disposable messagesConsume() {
        return delivery()
            .flatMap(delivery -> messageConsume(delivery, new String(delivery.getBody(), StandardCharsets.UTF_8)))
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    private Mono<ContactMessageHandlerResult> messageConsume(AcknowledgableDelivery ackDelivery, String messagePayload) {
        return Mono.just(messagePayload)
            .map(EmailAddressContactMessageSerializer::deserializeEmailAddressContactMessageAsJava)
            .flatMap(message -> Mono.from(messageHandler.handler(message)))
            .flatMap(handlerResult -> {
                if (handlerResult instanceof Failure) {
                    return Mono.error(((Failure) handlerResult).error());
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
