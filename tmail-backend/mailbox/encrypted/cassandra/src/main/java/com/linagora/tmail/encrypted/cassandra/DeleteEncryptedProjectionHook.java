package com.linagora.tmail.encrypted.cassandra;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.reactivestreams.Publisher;

import com.github.fge.lambdas.Throwing;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.linagora.tmail.encrypted.EncryptedEmailContentStore;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class DeleteEncryptedProjectionHook implements PreDeletionHook {
    private static final int CONCURRENCY = 8;

    static class DeletedMessageMailboxContext {
        private final MessageId messageId;
        private final Username owner;
        private final List<MailboxId> ownerMailboxes;

        DeletedMessageMailboxContext(MessageId messageId, Username owner, List<MailboxId> ownerMailboxes) {
            this.messageId = messageId;
            this.owner = owner;
            this.ownerMailboxes = ownerMailboxes;
        }

        MessageId getMessageId() {
            return messageId;
        }

        Username getOwner() {
            return owner;
        }

        List<MailboxId> getOwnerMailboxes() {
            return ownerMailboxes;
        }

        @Override
        public final boolean equals(Object o) {
            if (o instanceof DeletedMessageMailboxContext that) {
                return Objects.equals(this.messageId, that.getMessageId())
                    && Objects.equals(this.owner, that.getOwner())
                    && Objects.equals(this.ownerMailboxes, that.getOwnerMailboxes());
            }
            return false;
        }

        @Override
        public final int hashCode() {
            return Objects.hash(messageId, owner, ownerMailboxes);
        }
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
            .filter(Throwing.predicate(this::isMessageStillAccessible))
            .flatMap(deleteContext -> Mono.from(encryptedEmailContentStore.delete(deleteContext.getMessageId())))
            .then();
    }

    private List<MailboxId> getListMailBoxIds(MessageId messageId, MailboxSession session) throws MailboxException {
        return messageIdManager.getMessage(messageId, FetchGroup.HEADERS, session)
            .stream()
            .map(MessageResult::getMailboxId)
            .toList();
    }

    private boolean isMessageStillAccessible(DeletedMessageMailboxContext deleteContext) throws MailboxException {
        return new HashSet<>(deleteContext.getOwnerMailboxes())
            .containsAll(getListMailBoxIds(deleteContext.getMessageId(),
                sessionProvider.createSystemSession(deleteContext.getOwner())));
    }

    private Flux<DeletedMessageMailboxContext> getDeletedMessageMailboxContexts(DeleteOperation deleteOperation) {
        return Flux.fromIterable(deleteOperation.getDeletionMetadataList())
            .groupBy(MetadataWithMailboxId::getMailboxId)
            .flatMap(this::addOwnerToMetadata, CONCURRENCY);
    }

    private Flux<DeletedMessageMailboxContext> addOwnerToMetadata(GroupedFlux<MailboxId, MetadataWithMailboxId> groupedFlux) {
        return retrieveMailboxUser(groupedFlux.key())
            .flatMapMany(owner -> groupedFlux.map(metadata ->
                new DeletedMessageMailboxContext(metadata.getMessageMetaData().getMessageId(), owner, ImmutableList.of(metadata.getMailboxId()))));
    }

    private Mono<Username> retrieveMailboxUser(MailboxId mailboxId) {
        return mapperFactory.getMailboxMapper(systemSession)
            .findMailboxById(mailboxId)
            .map(Mailbox::getUser);
    }
}
