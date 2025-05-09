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

package com.linagora.tmail.contact;

import static com.linagora.tmail.OpenPaasModule.OPENPAAS_INJECTION_KEY;

import java.io.Closeable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.core.MailAddress;
import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.AccountId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;
import com.linagora.tmail.api.OpenPaasRestClient;
import com.linagora.tmail.dav.DavClientException;
import com.linagora.tmail.james.jmap.contact.ContactFields;
import com.linagora.tmail.james.jmap.contact.EmailAddressContact;
import com.linagora.tmail.james.jmap.contact.EmailAddressContactSearchEngine;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.Receiver;

public class SabreContactsConsumer implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SabreContactsConsumer.class);
    private static final boolean REQUEUE_ON_NACK = true;
    public static final String QUEUE_NAME_ADD = "sabre-contacts-queue-add";
    public static final String QUEUE_NAME_UPDATE = "sabre-contacts-queue-update";
    public static final String QUEUE_NAME_DELETE = "sabre-contacts-queue-delete";

    private final ReceiverProvider receiverProvider;
    private final EmailAddressContactSearchEngine contactSearchEngine;
    private final OpenPaasRestClient openPaasRestClient;
    private Disposable consumeAddedContactsDisposable;
    private Disposable consumeUpdatedContactsDisposable;
    private Disposable consumeDeletedContactsDisposable;

    @Inject
    public SabreContactsConsumer(@Named(OPENPAAS_INJECTION_KEY) ReactorRabbitMQChannelPool channelPool,
                                 EmailAddressContactSearchEngine contactSearchEngine,
                                 OpenPaasRestClient openPaasRestClient) {
        this.receiverProvider = channelPool::createReceiver;
        this.contactSearchEngine = contactSearchEngine;
        this.openPaasRestClient = openPaasRestClient;
    }

    @FunctionalInterface
    public interface ContactHandler {
        Mono<?> handleContact(AccountId ownerAccountId, SabreContactMessage contactMessage);
    }

    public void start() {
        consumeAddedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_ADD, this::handleAddContact);
        consumeUpdatedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_UPDATE, this::handleUpdateContact);
        consumeDeletedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_DELETE, this::handleDeleteContact);
    }

    public void restart() {
        Disposable previousAddedConsumer = consumeAddedContactsDisposable;
        Disposable previousUpdatedConsumer = consumeUpdatedContactsDisposable;
        Disposable previousDeletedConsumer = consumeDeletedContactsDisposable;
        consumeAddedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_ADD, this::handleAddContact);
        consumeUpdatedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_UPDATE, this::handleUpdateContact);
        consumeDeletedContactsDisposable = doConsumeContactMessages(QUEUE_NAME_DELETE, this::handleDeleteContact);
        Optional.ofNullable(previousAddedConsumer).ifPresent(Disposable::dispose);
        Optional.ofNullable(previousUpdatedConsumer).ifPresent(Disposable::dispose);
        Optional.ofNullable(previousDeletedConsumer).ifPresent(Disposable::dispose);
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
        return Mono.fromCallable(() -> SabreContactMessage.parseAMQPMessage(messagePayload))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .flatMap(contactMessage -> getAccountId(contactMessage.openPaasUserId())
                .flatMap(accountId -> contactHandler.handleContact(accountId, contactMessage))
                .then())
            .doOnSuccess(result -> {
                LOGGER.debug("Consumed contact successfully '{}'", result);
                ackDelivery.ack();
            })
            .onErrorResume(error -> {
                LOGGER.warn("Error when consume message", error);
                ackDelivery.nack(!REQUEUE_ON_NACK);
                return Mono.empty();
            });
    }

    private Mono<Void> handleAddContact(AccountId ownerAccountId, SabreContactMessage sabreContactMessage) {
        return Flux.fromIterable(sabreContactMessage.getContactFields())
            .flatMap(contactFields ->
                Mono.from(contactSearchEngine.index(ownerAccountId, contactFields, sabreContactMessage.getVCardUid())))
            .then();
    }

    private Mono<Void> handleUpdateContact(AccountId ownerAccountId, SabreContactMessage sabreContactMessage) {
        Mono<Map<MailAddress, ContactFields>> existingContacts = Flux.from(contactSearchEngine.list(ownerAccountId, sabreContactMessage.getVCardUid()))
            .collectList()
            .map(list -> list.stream()
                .collect(Collectors.toMap(addressContact -> addressContact.fields().address(), EmailAddressContact::fields)));

        Mono<Map<MailAddress, ContactFields>> incomingContacts = Mono.fromCallable(() -> sabreContactMessage.getContactFields()
            .stream()
            .collect(Collectors.toMap(ContactFields::address, Function.identity())));

        return Mono.zip(incomingContacts, existingContacts)
            .flatMap(tuple ->
                applyContactDiff(ownerAccountId, sabreContactMessage.getVCardUid(), tuple.getT1(), tuple.getT2()));
    }

    private Mono<Void> applyContactDiff(AccountId accountId,
                                        String vCardUid,
                                        Map<MailAddress, ContactFields> newContacts,
                                        Map<MailAddress, ContactFields> oldContacts) {
        Set<MailAddress> newAddresses = newContacts.keySet();
        Set<MailAddress> oldAddresses = oldContacts.keySet();

        Set<MailAddress> toAdd = Sets.difference(newAddresses, oldAddresses);
        Set<MailAddress> toDelete = Sets.difference(oldAddresses, newAddresses);
        Set<MailAddress> toUpdate = Sets.intersection(oldAddresses, newAddresses)
            .stream()
            .filter(address -> !(oldContacts.get(address).identifier().equals(newContacts.get(address).identifier())))
            .collect(Collectors.toSet());

        Mono<Void> addOp = Flux.fromIterable(toAdd)
            .flatMap(address -> Mono.from(contactSearchEngine.index(accountId, newContacts.get(address), vCardUid)))
            .then();

        Mono<Void> updateOp = Flux.fromIterable(toUpdate)
            .flatMap(address -> Mono.from(contactSearchEngine.update(accountId, newContacts.get(address), vCardUid)))
            .then();

        Mono<Void> deleteOp = Flux.fromIterable(toDelete)
            .flatMap(address -> Mono.from(contactSearchEngine.delete(accountId, address, vCardUid)))
            .then();

        return Mono.when(addOp, updateOp, deleteOp);
    }

    private Mono<Void> handleDeleteContact(AccountId ownerAccountId, SabreContactMessage sabreContactMessage) {
        return Flux.fromIterable(sabreContactMessage.getMailAddresses())
            .flatMap(mailAddress -> Mono.from(contactSearchEngine.delete(ownerAccountId, mailAddress, sabreContactMessage.getVCardUid()))
                .onErrorResume(error -> {
                    LOGGER.warn("Failed to delete contact: {} for accountId {} ", mailAddress, ownerAccountId.getIdentifier(), error);
                    return Mono.empty();
                }))
            .then();
    }

    @Override
    public void close() {
        Optional.ofNullable(consumeAddedContactsDisposable).ifPresent(Disposable::dispose);
        Optional.ofNullable(consumeUpdatedContactsDisposable).ifPresent(Disposable::dispose);
        Optional.ofNullable(consumeDeletedContactsDisposable).ifPresent(Disposable::dispose);
    }

    private Mono<AccountId> getAccountId(String openpaasUserId) {
        return openPaasRestClient.retrieveMailAddress(openpaasUserId)
            .map(mailAddress -> AccountId.fromUsername(Username.fromMailAddress(mailAddress)))
            .switchIfEmpty(Mono.error(new DavClientException("Unable to find user in Dav server with id '%s'".formatted(openpaasUserId))));
    }
}