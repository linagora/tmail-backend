package com.linagora.tmail.contact;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import java.io.Closeable;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;
import com.rabbitmq.client.BuiltinExchangeType;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;


public class OpenPaasContactsConsumer implements Startable, Closeable {
    private static final Logger LOGGER = LoggerFactory.getLogger(OpenPaasContactsConsumer.class);

    private static final boolean REQUEUE_ON_NACK = true;
    public static final String EXCHANGE_NAME = "contacts:contact:add";
    public static final String QUEUE_NAME = "openpaas-contacts-queue";
    public static final String DEAD_LETTER = QUEUE_NAME + "-dead-letter";

    private Disposable consumeContactsDisposable;
    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenPaasRestClient openPaasRestClient;

    @Inject
    public OpenPaasContactsConsumer(ReactorRabbitMQChannelPool channelPool,
                                    RabbitMQConfiguration commonRabbitMQConfiguration,
                                    EmailAddressContactSearchEngine contactSearchEngine,
                                    OpenPaasRestClient openPaasRestClient) {
        this.receiverProvider = channelPool::createReceiver;
        this.sender = channelPool.getSender();
        this.commonRabbitMQConfiguration = commonRabbitMQConfiguration;
        this.contactSearchEngine = contactSearchEngine;
        this.openPaasRestClient = openPaasRestClient;
    }

    public void start() {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(EXCHANGE_NAME)
                    .durable(DURABLE).type(BuiltinExchangeType.FANOUT.getType())),
                sender.declareQueue(QueueSpecification
                    .queue(DEAD_LETTER)
                    .durable(DURABLE)
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder().build())),
                sender.declareQueue(QueueSpecification
                    .queue(QUEUE_NAME)
                    .durable(DURABLE)
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder()
                        .deadLetter(DEAD_LETTER)
                        .build())),
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
            .flatMap(delivery -> messageConsume(delivery, delivery.getBody()))
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery() {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(QUEUE_NAME),
            Receiver::close);
    }

    private Mono<EmailAddressContact> messageConsume(AcknowledgableDelivery ackDelivery, byte[] messagePayload) {
        return Mono.just(messagePayload)
            .map(ContactAddedRabbitMqMessage::fromJSON)
            .flatMap(this::handleMessage)
            .doOnSuccess(result -> {
                LOGGER.info("Consumed contact successfully '{}'", result);
                ackDelivery.ack();
            })
            .onErrorResume(error -> {
                LOGGER.error("Error when consume message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    private Mono<EmailAddressContact> handleMessage(ContactAddedRabbitMqMessage contactAddedMessage) {
        LOGGER.debug("Consumed jCard object message: {}", contactAddedMessage);
        Optional<ContactFields> maybeContactFields = contactAddedMessage.vcard().asContactFields();
        return maybeContactFields.map(
                openPaasContact -> openPaasRestClient.retrieveMailAddress(contactAddedMessage.userId())
                    .map(this::getAccountIdFromMailAddress)
                    .flatMap(ownerAccountId -> indexContactIfNeeded(ownerAccountId, openPaasContact)))
            .orElse(Mono.empty());
    }

    private Mono<EmailAddressContact> indexContactIfNeeded(AccountId ownerAccountId, ContactFields openPaasContact) {
        return Mono.from(contactSearchEngine.get(ownerAccountId, openPaasContact.address()))
            .onErrorResume(ContactNotFoundException.class, e -> Mono.empty())
            .switchIfEmpty(
                Mono.from(contactSearchEngine.index(ownerAccountId, openPaasContact)))
            .flatMap(existingContact -> {
                if (!openPaasContact.firstname().isBlank()) {
                    return Mono.from(contactSearchEngine.index(ownerAccountId, openPaasContact));
                } else {
                    return Mono.empty();
                }
            });
    }

    private AccountId getAccountIdFromMailAddress(MailAddress mailAddress) {
        return AccountId.fromUsername(Username.fromMailAddress(mailAddress));
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeContactsDisposable)
            .ifPresent(Disposable::dispose);
    }
}
