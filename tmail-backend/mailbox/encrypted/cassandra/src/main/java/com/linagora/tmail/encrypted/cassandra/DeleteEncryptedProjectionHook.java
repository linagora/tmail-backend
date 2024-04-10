package com.linagora.tmail.encrypted.cassandra;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.encrypted.EncryptedEmailContentStore;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class DeleteEncryptedProjectionHook implements PreDeletionHook {
    private static final int CONCURRENCY = 8;

    record DeletedMessageMailboxContext(MessageId messageId, Username owner, List<MailboxId> ownerMailboxes) {
    }

    private final EncryptedEmailContentStore encryptedEmailContentStore;
    private final MailboxSession systemSession;
    private final MessageIdManager messageIdManager;
    private final SessionProvider sessionProvider;
    private final MailboxSessionMapperFactory mapperFactory;

    @Inject
    public DeleteEncryptedProjectionHook(EncryptedEmailContentStore encryptedEmailContentStore,
                                         SessionProvider sessionProvider,
                                         MessageIdManager messageIdManager,
                                         MailboxSessionMapperFactory mapperFactory) {
        this.encryptedEmailContentStore = encryptedEmailContentStore;
        this.systemSession = sessionProvider.createSystemSession(Username.of(getClass().getName()));
        this.messageIdManager = messageIdManager;
        this.sessionProvider = sessionProvider;
        this.mapperFactory = mapperFactory;
    }

    @Override
    public Publisher<Void> notifyDelete(DeleteOperation deleteOperation) {
        Preconditions.checkNotNull(deleteOperation);
        return getDeletedMessageMailboxContexts(deleteOperation)
            .filterWhen(this::isMessageStillAccessible)
            .flatMap(deleteContext -> Mono.from(encryptedEmailContentStore.delete(deleteContext.messageId())))
            .then();
    }

    private Mono<Boolean> isMessageStillAccessible(DeletedMessageMailboxContext deleteContext) {
        return Flux.from(messageIdManager.getMessagesReactive(ImmutableList.of(deleteContext.messageId()), FetchGroup.MINIMAL, sessionProvider.createSystemSession(deleteContext.owner())))
            .map(MessageResult::getMailboxId)
            .collect(Collectors.toSet())
            .map(listMailbox -> new HashSet<>(deleteContext.ownerMailboxes()).containsAll(listMailbox));
    }

    private Flux<DeletedMessageMailboxContext> getDeletedMessageMailboxContexts(DeleteOperation deleteOperation) {
        return Flux.fromIterable(deleteOperation.getDeletionMetadataList())
            .groupBy(MetadataWithMailboxId::getMailboxId)
            .flatMap(this::addOwnerToMetadata, CONCURRENCY);
    }

    private Flux<DeletedMessageMailboxContext> addOwnerToMetadata(GroupedFlux<MailboxId, MetadataWithMailboxId> groupedFlux) {
        return retrieveMailboxUser(groupedFlux.key())
            .flatMapMany(owner -> groupedFlux.map(metadata ->
                new DeletedMessageMailboxContext(metadata.getMessageId(), owner, ImmutableList.of(metadata.getMailboxId()))));
    }

    private Mono<Username> retrieveMailboxUser(MailboxId mailboxId) {
        return mapperFactory.getMailboxMapper(systemSession)
            .findMailboxById(mailboxId)
            .map(Mailbox::getUser);
    }
}
