package com.linagora.tmail.james.jmap.contact;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.internet.AddressException;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.lifecycle.api.Startable;

import com.google.gson.Gson;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;


public class OpenPaasContactsConsumer implements Startable, Closeable {
    public static final String TOPIC = "contacts:contact:add";
    public static final String QUEUE_NAME = "ConsumeOpenPaasContactsQueue";
    private Disposable consumeContactsDisposable;

    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final Gson gson = new Gson();

    // TODO: Create a separate RabbitMQ module for OpenPaaS communication so the injected channel pool
    //  would be custom configured
    @Inject
    public OpenPaasContactsConsumer(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) ReceiverProvider receiverProvider,
                                                 @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) Sender sender,
                                                 RabbitMQConfiguration commonRabbitMQConfiguration,
                                                 EmailAddressContactSearchEngine contactSearchEngine) {
        this.receiverProvider = receiverProvider;
        this.sender = sender;
        this.commonRabbitMQConfiguration = commonRabbitMQConfiguration;
        this.contactSearchEngine = contactSearchEngine;
    }

    @Override
    public void start() {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(TOPIC)
                    .durable(DURABLE)),
                sender.declareQueue(QueueSpecification
                    .queue(QUEUE_NAME)
                    .durable(evaluateDurable(DURABLE, commonRabbitMQConfiguration.isQuorumQueuesUsed()))),
                sender.bind(BindingSpecification.binding()
                    .exchange(TOPIC)
                    .queue(QUEUE_NAME)
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();

        consumeContactsDisposable = doConsumeContactMessages();
    }

    private Disposable doConsumeContactMessages() {
        return delivery()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(delivery ->
                messageConsume(delivery, new String(delivery.getBody(), StandardCharsets.UTF_8)));
    }

    public Flux<AcknowledgableDelivery> delivery() {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(QUEUE_NAME),
            Receiver::close);
    }

    private void messageConsume(AcknowledgableDelivery ackDelivery, String messagePayload) {
        Mono.just(messagePayload)
            .map(message -> gson.fromJson(message, OpenPaasContactMessage.class))
            .handle((msg, sink) -> {
                handleMessage(msg, sink);
                ackDelivery.ack();
            });
    }

    private void handleMessage(OpenPaasContactMessage message, SynchronousSink<Object> sink) {
        try {
            String firstname = message.getFirstname();
            Username username = Username.of(message.getUsername());
            String lastname = message.getLastname();
            MailAddress mailAddress = new MailAddress(message.getEmail());
            Mono.from(contactSearchEngine.index(AccountId.fromUsername(username),
                new ContactFields(mailAddress, firstname, lastname))).block();

        } catch (AddressException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() throws IOException {
        consumeContactsDisposable.dispose();
    }
}
