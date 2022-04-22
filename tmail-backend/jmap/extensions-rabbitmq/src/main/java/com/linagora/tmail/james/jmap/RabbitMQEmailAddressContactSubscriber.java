package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessage;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessageHandler;
import com.linagora.tmail.james.jmap.json.EmailAddressContactMessageSerializer;
import com.rabbitmq.client.Delivery;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

public class RabbitMQEmailAddressContactSubscriber implements Closeable {
    static class RabbitMQConfiguration {
        public static RabbitMQConfiguration from(Configuration config) {
            return new RabbitMQConfiguration(
                config.getString("address.contact.exchange", "AddressContactExchangeDefault"),
                config.getString("address.contact.queue", "AddressContactQueueDefault"),
                config.getString("address.contact.routingKey", "AddressContactRoutingKeyDefault"));
        }

        private final String exchangeName;
        private final String queueName;
        private final String routingKey;

        public RabbitMQConfiguration(String exchangeName, String queueName, String routingKey) {
            Preconditions.checkArgument(StringUtils.isNotBlank(exchangeName));
            Preconditions.checkArgument(StringUtils.isNotBlank(queueName));
            Preconditions.checkArgument(StringUtils.isNotBlank(routingKey));
            this.exchangeName = exchangeName;
            this.queueName = queueName;
            this.routingKey = routingKey;
        }

        public String getExchangeName() {
            return exchangeName;
        }

        public String getQueueName() {
            return queueName;
        }

        public String getRoutingKey() {
            return routingKey;
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQEmailAddressContactSubscriber.class);

    private final RabbitMQConfiguration rabbitMQConfiguration;
    private final ReceiverProvider receiverProvider;
    private final Sinks.Many<EmailAddressContactMessage> listener;
    private final EmailAddressContactMessageHandler messageHandler;
    private final Sender sender;
    private Disposable listenQueueHandle;
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
        this.listener = Sinks.many().multicast().directBestEffort();
    }

    public void start() {
        sender.declareExchange(ExchangeSpecification.exchange(rabbitMQConfiguration.getExchangeName())).block();
        sender.declare(QueueSpecification.queue(rabbitMQConfiguration.getQueueName()).durable(DURABLE)).block();
        sender.bind(BindingSpecification.binding(rabbitMQConfiguration.getExchangeName(), rabbitMQConfiguration.getRoutingKey(), rabbitMQConfiguration.getQueueName())).block();
        listenQueueHandle = consumeQueue();
        messageConsume = messageConsume();
    }

    private Disposable consumeQueue() {
        return Flux.using(receiverProvider::createReceiver,
                receiver -> receiver.consumeAutoAck(rabbitMQConfiguration.getQueueName()),
                Receiver::close)
            .subscribeOn(Schedulers.elastic())
            .map(this::toMessage)
            .handle(publishIfPresent())
            .subscribe(event -> listener.emitNext(event, (signalType, emission) -> true));
    }

    private Optional<EmailAddressContactMessage> toMessage(Delivery delivery) {
        String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
        try {
            return Optional.of(EmailAddressContactMessageSerializer.deserializeEmailAddressContactMessageAsJava(message));
        } catch (Exception e) {
            LOGGER.error("Unable to deserialize '{}'", message, e);
            return Optional.empty();
        }
    }

    public Flux<EmailAddressContactMessage> receivedMessages() {
        return listener.asFlux();
    }

    private Disposable messageConsume() {
        return receivedMessages()
            .flatMap(message -> Mono.from(messageHandler.handler(message))
                .onErrorResume(error -> {
                    LOGGER.error("Error when consume message '{}'", message, error);
                    return Mono.empty();
                }))
            .subscribeOn(Schedulers.elastic())
            .subscribe();
    }

    @Override
    public void close() {
        Optional.ofNullable(listenQueueHandle).ifPresent(Disposable::dispose);
        Optional.ofNullable(messageConsume).ifPresent(Disposable::dispose);
    }
}
