package com.linagora.tmail.contact;

import static com.linagora.tmail.OpenPaasModule.OPENPAAS_INJECTION_KEY;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.backends.rabbitmq.QueueArguments;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.lifecycle.api.Startable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.configuration.OpenPaasConfiguration;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.ContactNotFoundException;
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
    public static final String EXCHANGE_NAME_ADD = "contacts:contact:add";
    public static final String EXCHANGE_NAME_UPDATE = "contacts:contact:update";
    public static final String EXCHANGE_NAME_DELETE = "contacts:contact:delete";
    public static final String QUEUE_NAME_ADD = "openpaas-contacts-queue-add";
    public static final String QUEUE_NAME_UPDATE = "openpaas-contacts-queue-update";
    public static final String QUEUE_NAME_DELETE = "openpaas-contacts-queue-delete";
    public static final String DEAD_LETTER_ADD = "openpaas-contacts-queue-add-dead-letter";
    public static final String DEAD_LETTER_UPDATE = "openpaas-contacts-queue-update-dead-letter";
    public static final String DEAD_LETTER_DELETE = "openpaas-contacts-queue-delete-dead-letter";
    private static final boolean FALLBACK_CLASSIC_QUEUES_VERSION_1 = Boolean.parseBoolean(System.getProperty("fallback.classic.queues.v1", "false"));

    private final ReceiverProvider receiverProvider;
    private final Sender sender;
    private final RabbitMQConfiguration commonRabbitMQConfiguration;
    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final OpenPaasRestClient openPaasRestClient;
    private Disposable consumeAddedContactsDisposable;
    private Disposable consumeUpdatedContactsDisposable;
    private Disposable consumeDeletedContactsDisposable;
    private final OpenPaasConfiguration openPaasConfiguration;

    @Inject
    public OpenPaasContactsConsumer(@Named(OPENPAAS_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                    @Named(OPENPAAS_INJECTION_KEY) RabbitMQConfiguration commonRabbitMQConfiguration,
                                    EmailAddressContactSearchEngine contactSearchEngine,
                                    OpenPaasRestClient openPaasRestClient,
                                    OpenPaasConfiguration openPaasConfiguration) {
        this.receiverProvider = channelPool::createReceiver;
        this.sender = channelPool.getSender();
        this.commonRabbitMQConfiguration = commonRabbitMQConfiguration;
        this.contactSearchEngine = contactSearchEngine;
        this.openPaasRestClient = openPaasRestClient;
        this.openPaasConfiguration = openPaasConfiguration;
    }

    @FunctionalInterface
    public interface ContactHandler {
        Mono<?> handleContact(AccountId ownerAccountId, ContactFields openPaasContact);
    }

    public void start() {
        startExchange(EXCHANGE_NAME_ADD, QUEUE_NAME_ADD, DEAD_LETTER_ADD);
        startExchange(EXCHANGE_NAME_UPDATE, QUEUE_NAME_UPDATE, DEAD_LETTER_UPDATE);
        startExchange(EXCHANGE_NAME_DELETE, QUEUE_NAME_DELETE, DEAD_LETTER_DELETE);
        consumeAddedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_ADD, this::indexContactIfNeeded);
        consumeUpdatedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_UPDATE, this::updateContact);
        consumeDeletedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_DELETE, this::deleteContact);
    }

    public void startExchange(String exchange, String queue, String deadLetter) {
        Flux.concat(
                sender.declareExchange(ExchangeSpecification.exchange(exchange)
                    .durable(DURABLE).type(BuiltinExchangeType.FANOUT.getType())),
                sender.declareQueue(QueueSpecification
                    .queue(deadLetter)
                    .durable(DURABLE)
                    .arguments(evaluateQueueArguments()
                        .build())),
                sender.declareQueue(QueueSpecification
                    .queue(queue)
                    .durable(DURABLE)
                    .arguments(evaluateQueueArguments()
                        .deadLetter(deadLetter)
                        .build())),
                sender.bind(BindingSpecification.binding()
                    .exchange(exchange)
                    .queue(queue)
                    .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();
    }

    public void restart() {
        Disposable previousAddedConsumer = consumeAddedContactsDisposable;
        Disposable previousUpdatedConsumer = consumeUpdatedContactsDisposable;
        Disposable previousDeletedConsumer = consumeDeletedContactsDisposable;
        consumeAddedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_ADD, this::indexContactIfNeeded);
        consumeUpdatedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_UPDATE, this::updateContact);
        consumeDeletedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_DELETE, this::deleteContact);
        Optional.ofNullable(previousAddedConsumer).ifPresent(Disposable::dispose);
        Optional.ofNullable(previousUpdatedConsumer).ifPresent(Disposable::dispose);
        Optional.ofNullable(previousDeletedConsumer).ifPresent(Disposable::dispose);
    }

    private QueueArguments.Builder evaluateQueueArguments() {
        if (!openPaasConfiguration.quorumQueuesBypass()) {
            return commonRabbitMQConfiguration.workQueueArgumentsBuilder();
        }
        if (!FALLBACK_CLASSIC_QUEUES_VERSION_1) {
            return QueueArguments.builder()
                .classicQueueVersion(2);
        }
        return QueueArguments.builder();
    }

    private Disposable doConsumeContactMessages(String queue, ContactHandler contactHandler) {
        return delivery(queue)
            .flatMap(delivery -> messageConsume(delivery, delivery.getBody(), contactHandler))
            .subscribe();
    }

    public Flux<AcknowledgableDelivery> delivery(String queue) {
        return Flux.using(receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(queue),
            Receiver::close);
    }

    private Mono<?> messageConsume(AcknowledgableDelivery ackDelivery, byte[] messagePayload, ContactHandler contactHandler) {
        return Mono.fromCallable(() -> ContactRabbitMqMessage.fromJSON(messagePayload))
            .filter(contactMessage -> !StringUtils.isEmpty(contactMessage.openPaasUserId()))
            .switchIfEmpty(Mono.defer(() -> {
                LOGGER.warn("OpenPaas user id is empty, skipping contact message: {}", new String(messagePayload, StandardCharsets.UTF_8));
                return Mono.empty();
            }))
            .flatMap(contactMessage -> handleMessage(contactMessage, contactHandler))
            .doOnSuccess(result -> {
                LOGGER.debug("Consumed contact successfully '{}'", result);
                ackDelivery.ack();
            })
            .onErrorResume(error -> {
                LOGGER.error("Error when consume message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    private Mono<Void> handleMessage(ContactRabbitMqMessage contactMessage, ContactHandler contactHandler) {
        LOGGER.trace("Consumed jCard object message: {}", contactMessage);
        return Mono.fromCallable(() -> contactMessage.vcard().asContactFields())
            .flatMap(openPaasContacts -> openPaasRestClient.retrieveMailAddress(contactMessage.openPaasUserId())
                .map(this::getAccountIdFromMailAddress)
                .flatMap(ownerAccountId -> Flux.fromIterable(openPaasContacts)
                    .flatMap(contact -> contactHandler.handleContact(ownerAccountId, contact))
                    .then()));
    }

    private Mono<EmailAddressContact> indexContactIfNeeded(AccountId ownerAccountId, ContactFields openPaasContact) {
        return Mono.from(contactSearchEngine.get(ownerAccountId, openPaasContact.address()))
            .onErrorResume(ContactNotFoundException.class, e -> Mono.empty())
            .switchIfEmpty(Mono.from(contactSearchEngine.index(ownerAccountId, openPaasContact)))
            .flatMap(existingContact -> {
                if (!openPaasContact.firstname().isBlank()) {
                    return Mono.from(contactSearchEngine.index(ownerAccountId, openPaasContact));
                } else {
                    return Mono.empty();
                }
            });
    }

    private Mono<EmailAddressContact> updateContact(AccountId ownerAccountId, ContactFields openPaasContact) {
        return Mono.from(contactSearchEngine.get(ownerAccountId, openPaasContact.address()))
            .flatMap(existingContact -> Mono.from(contactSearchEngine.update(ownerAccountId, openPaasContact)))
            .onErrorResume(ContactNotFoundException.class, e -> {
                LOGGER.warn("Contact not found for update: {}", openPaasContact.address());
                return Mono.from(contactSearchEngine.index(ownerAccountId, openPaasContact));
            });
    }

    private Mono<Void> deleteContact(AccountId ownerAccountId, ContactFields openPaasContact) {
        return Mono.from(contactSearchEngine.delete(ownerAccountId, openPaasContact.address()))
            .onErrorResume(error -> {
                LOGGER.warn("Failed to delete contact: {}", openPaasContact, error);
                return Mono.empty();
            });
    }

    private AccountId getAccountIdFromMailAddress(MailAddress mailAddress) {
        return AccountId.fromUsername(Username.fromMailAddress(mailAddress));
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeAddedContactsDisposable).ifPresent(Disposable::dispose);
        Optional.ofNullable(consumeUpdatedContactsDisposable).ifPresent(Disposable::dispose);
        Optional.ofNullable(consumeDeletedContactsDisposable).ifPresent(Disposable::dispose);
    }
}
