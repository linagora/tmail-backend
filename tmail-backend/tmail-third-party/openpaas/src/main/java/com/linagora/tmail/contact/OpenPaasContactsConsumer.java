package com.linagora.tmail.contact;

import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.BiFunction;

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
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.james.jmap.EmailAddressContactInjectKeys;
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

    private static final boolean REQUEUE_ON_NACK = false;
    public static final String EXCHANGE_NAME = "contacts:contact:add";
    public static final String QUEUE_NAME = "ConsumeOpenPaasContactsQueue";
    public static final String DEAD_LETTER_EXCHANGE = "contacts:contact:add:dead:letter";
    public static final String DEAD_LETTER_QUEUE = "ConsumeOpenPaasContactsQueue-dead-letter";

    private Disposable consumeContactsDisposable;
    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenPaasRestClient openPaasRestClient;

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
                sender.declareExchange(ExchangeSpecification.exchange(DEAD_LETTER_EXCHANGE)
                    .durable(DURABLE)),
                sender.declareExchange(ExchangeSpecification.exchange(DEAD_LETTER_EXCHANGE)
                    .durable(DURABLE)),
                sender.declareQueue(QueueSpecification
                    .queue(DEAD_LETTER_QUEUE)
                    .durable(DURABLE)
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder().build())),
                sender.declareQueue(QueueSpecification
                    .queue(DEAD_LETTER_QUEUE)
                    .durable(DURABLE)
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder().build())),
                sender.declareQueue(QueueSpecification
                    .queue(QUEUE_NAME)
                    .durable(DURABLE)
                    .arguments(commonRabbitMQConfiguration.workQueueArgumentsBuilder()
                    .put("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
                    .put("x-dead-letter-routing-key", EMPTY_ROUTING_KEY)
                    .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(EXCHANGE_NAME)
                    .queue(QUEUE_NAME)
                    .routingKey(EMPTY_ROUTING_KEY)),
                sender.bind(BindingSpecification.binding()
                    .exchange(DEAD_LETTER_EXCHANGE)
                    .queue(DEAD_LETTER_QUEUE)
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();

        consumeContactsDisposable = doConsumeContactMessages();
    }

    private Disposable doConsumeContactMessages() {
        return delivery()
            .flatMap(delivery -> messageConsume(delivery, new String(delivery.getBody(), StandardCharsets.UTF_8)))
            .doOnError(e -> LOGGER.error("Failed to consume contact message", e))
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery() {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(QUEUE_NAME),
            Receiver::close);
    }

    private Mono<EmailAddressContact> messageConsume(AcknowledgableDelivery ackDelivery, String messagePayload) {
        return Mono.just(messagePayload)
            .map(this::parseContactAddedRabbitMqMessage)
            .log()
            .flatMap(this::handleMessage)
            .doOnSuccess(input -> ackDelivery.ack())
            .doOnError(e -> ackDelivery.nack(REQUEUE_ON_NACK))
            .onErrorResume(e -> Mono.error(new RuntimeException("Failed to consume OpenPaaS added contact message", e)));
    }

    private ContactAddedRabbitMqMessage parseContactAddedRabbitMqMessage(String message) {
        try {
            return objectMapper.readValue(message, ContactAddedRabbitMqMessage.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse ContactAddedRabbitMqMessage", e);
        }
    }

    private Mono<EmailAddressContact> handleMessage(ContactAddedRabbitMqMessage contactAddedMessage) {
        LOGGER.info("Consumed jCard object message: {}", contactAddedMessage);

        return openPaasRestClient.retrieveMailAddress(contactAddedMessage.userId())
            .map(ownerMailAddress -> AccountId.fromUsername(Username.fromMailAddress(ownerMailAddress)))
            .flatMap(ownerAccountId ->
                Mono.justOrEmpty(toContactFields(contactAddedMessage.vcard()))
                    .flatMap(contactFields -> doAddContact(ownerAccountId, contactFields)));
    }

    private Mono<EmailAddressContact> doAddContact(AccountId ownerAccountId, ContactFields contactFields) {
        return Mono.from(contactSearchEngine.index(ownerAccountId, contactFields));
    }

    private Optional<ContactFields> toContactFields(JCardObject jCardObject) {
        Optional<String> contactFullnameOpt = jCardObject.fnOpt();
        Optional<MailAddress> contactMailAddressOpt = jCardObject.emailOpt()
            .flatMap(contactEmail -> {
                try {
                    return Optional.of(new MailAddress(contactEmail));
                } catch (AddressException e) {
                    LOGGER.warn("Invalid contact email address: {}", contactEmail, e);
                    return Optional.empty();
                }
            });

        return combineOptionals(contactFullnameOpt, contactMailAddressOpt,
            (contactFullname, contactMailAddress) ->
                new ContactFields(contactMailAddress, contactFullname, contactFullname));
    }

    private static <T,K,V> Optional<V> combineOptionals(Optional<T> opt1, Optional<K> opt2, BiFunction<T, K, V> f) {
        return opt1.flatMap(t1 -> opt2.map(t2 -> f.apply(t1, t2)));
    }

    @Override
    public void close() throws IOException {
        if (consumeContactsDisposable != null) {
            consumeContactsDisposable.dispose();
        }
    }
}
