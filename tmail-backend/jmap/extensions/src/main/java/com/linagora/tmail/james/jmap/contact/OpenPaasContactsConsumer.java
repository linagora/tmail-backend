package com.linagora.tmail.james.jmap.contact;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.internet.AddressException;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
import com.rabbitmq.client.BuiltinExchangeType;

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


public class OpenPaasContactsConsumer implements Startable, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasContactsConsumer.class);

    public static final String EXCHANGE_NAME = "contacts:contact:add";
    public static final String QUEUE_NAME = "ConsumeOpenPaasContactsQueue";
    private Disposable consumeContactsDisposable;

    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenPaasRestClient openPaasRestClient;

    // TODO: Create a separate RabbitMQ module for OpenPaaS communication so the injected channel pool
    //  would be custom configured
    @Inject
    public OpenPaasContactsConsumer(@Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) ReceiverProvider receiverProvider,
                                    @Named(EmailAddressContactInjectKeys.AUTOCOMPLETE) Sender sender,
                                    RabbitMQConfiguration commonRabbitMQConfiguration,
                                    EmailAddressContactSearchEngine contactSearchEngine,
                                    OpenPaasRestClient openPaasRestClient) {
        this.receiverProvider = receiverProvider;
        this.sender = sender;
        this.commonRabbitMQConfiguration = commonRabbitMQConfiguration;
        this.contactSearchEngine = contactSearchEngine;
        this.openPaasRestClient = openPaasRestClient;
    }

    public void start() {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME)
                    .durable(DURABLE).type(BuiltinExchangeType.FANOUT.getType())),
                sender.declareQueue(QueueSpecification
                    .queue(QUEUE_NAME)
                    .durable(DURABLE)),
                sender.bind(BindingSpecification.binding()
                    .exchange(EXCHANGE_NAME)
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
            .<ContactAddedRabbitMqMessage>handle((message, sink) -> {
                try {
                    sink.next(objectMapper.readValue(message, ContactAddedRabbitMqMessage.class));
                } catch (JsonProcessingException e) {
                    sink.error(new RuntimeException(e));
                }
            })
            .handle((msg, sink) -> {
                handleMessage(msg).block();
                ackDelivery.ack();
            })
            .onErrorResume(e -> {
                LOGGER.error("Failed to consume OpenPaaS added contact message", e);
                return Mono.empty();
            }).subscribe();
    }

    private Mono<Void> handleMessage(ContactAddedRabbitMqMessage contactAddedMessage) {
        LOGGER.info("Consumed jCard object message: {}", contactAddedMessage);

        String openPaasOwnerId = contactAddedMessage.userId();
        return openPaasRestClient.getUserById(openPaasOwnerId)
            .map(OpenPaasUserResponse::preferredEmail)
            .mapNotNull(ownerEmail -> {
                JCardObject jCardObject = contactAddedMessage.vcard();

                String contactFullname = jCardObject.fn();
                Optional<MailAddress> contactMailAddressOpt = jCardObject.emailOpt()
                    .flatMap(contactEmail -> {
                        try {
                            return Optional.of(new MailAddress(contactEmail));
                        } catch (AddressException e) {
                            return Optional.empty();
                        }
                    });

                if (contactMailAddressOpt.isEmpty()) {
                    return Mono.empty();
                }

                try {
                    MailAddress ownerMailAddress = new MailAddress(ownerEmail);
                    AccountId ownerAccountId =
                        AccountId.fromUsername(Username.fromMailAddress(ownerMailAddress));

                    return Mono.from(contactSearchEngine.index(ownerAccountId,
                            new ContactFields(contactMailAddressOpt.get(), contactFullname, "")
                        )).block();

                } catch (AddressException e) {
                    return Mono.error(new RuntimeException(
                        "The user mail address fetched from OpenPaas is invalid", e));
                }
            }).then();
    }

    @Override
    public void close() throws IOException {
        if (consumeContactsDisposable != null) {
            consumeContactsDisposable.dispose();
        }
    }
}
