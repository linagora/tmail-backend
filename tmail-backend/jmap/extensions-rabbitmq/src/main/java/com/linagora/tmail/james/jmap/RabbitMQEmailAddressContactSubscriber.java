
package com.linagora.tmail.james.jmap;

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.events.EventBus;
import org.apache.james.events.RegistrationKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactMessage;
import com.linagora.tmail.james.jmap.contact.TmailEvent;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMQEmailAddressContactSubscriber.class);

    // TODO: Should parse from configure file
    public static final String EXCHANGE_NAME = "EmailAddressContactExchange";
    public static final String QUEUE_NAME = "EmailAddressContactQueue";
    public static final String ROUTING_KEY = "EmailAddressContactRoutingKey";
    private static final Set<RegistrationKey> EVENT_REGISTRATION_KEY = ImmutableSet.of();

    private final ReceiverProvider receiverProvider;
    private final Sinks.Many<EmailAddressContactMessage> listener;
    private final Sender sender;
    private final EventBus eventBus;
    private Disposable listenQueueHandle;

    @Inject
    public RabbitMQEmailAddressContactSubscriber(ReceiverProvider receiverProvider, Sender sender,
                                                 @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) EventBus eventBus) {
        this.receiverProvider = receiverProvider;
        this.sender = sender;
        this.eventBus = eventBus;
        this.listener = Sinks.many().multicast().directBestEffort();
    }

    public void start() {
        sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME)).block();
        sender.declare(QueueSpecification.queue(QUEUE_NAME).durable(!DURABLE).autoDelete(AUTO_DELETE)).block();
        sender.bind(BindingSpecification.binding(EXCHANGE_NAME, ROUTING_KEY, QUEUE_NAME)).block();
        listenQueueHandle = consumeQueue();
    }

    private Disposable consumeQueue() {
        return Flux.using(receiverProvider::createReceiver,
                receiver -> receiver.consumeAutoAck(QUEUE_NAME),
                Receiver::close)
            .subscribeOn(Schedulers.elastic())
            .map(this::toMessage)
            .handle(publishIfPresent())
            .subscribe(event -> listener.emitNext(event, Sinks.EmitFailureHandler.FAIL_FAST));
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

    private Mono<Void> dispatchEvent(TmailEvent tmailEvent) {
        return eventBus.dispatch(tmailEvent, EVENT_REGISTRATION_KEY);
    }

    @Override
    public void close() {
        Optional.ofNullable(listenQueueHandle).ifPresent(Disposable::dispose);
    }
}
