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

package com.linagora.tmail.encrypted;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MetadataWithMailboxId;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.extension.PreDeletionHook;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Deletes the encrypted projection of a message (encrypted preview, bodies, attachments) once the message
 * is no longer present in any mailbox but the ones it is being deleted from.
 *
 * Relies on the mappers rather than on the MessageIdManager, which depends on the pre-deletion hooks.
 */
public class DeleteEncryptedProjectionHook implements PreDeletionHook {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteEncryptedProjectionHook.class);
    private static final int CONCURRENCY = 8;

    private final EncryptedEmailContentStore encryptedEmailContentStore;
    private final MailboxSession systemSession;
    private final MailboxSessionMapperFactory mapperFactory;

    @Inject
    public DeleteEncryptedProjectionHook(EncryptedEmailContentStore encryptedEmailContentStore,
                                         SessionProvider sessionProvider,
                                         MailboxSessionMapperFactory mapperFactory) {
        this.encryptedEmailContentStore = encryptedEmailContentStore;
        this.systemSession = sessionProvider.createSystemSession(Username.of(getClass().getName()));
        this.mapperFactory = mapperFactory;
    }

    @Override
    public Publisher<Void> notifyDelete(DeleteOperation deleteOperation) {
        Preconditions.checkNotNull(deleteOperation);
        return Flux.fromIterable(deleteOperation.getDeletionMetadataList())
            .collectMultimap(MetadataWithMailboxId::getMessageId, MetadataWithMailboxId::getMailboxId)
            .flatMapIterable(Map::entrySet)
            .filterWhen(entry -> isOnlyInDeletedMailboxes(entry.getKey(), entry.getValue()), CONCURRENCY)
            .flatMap(entry -> deleteProjection(entry.getKey()), CONCURRENCY)
            .then();
    }

    private Mono<Boolean> isOnlyInDeletedMailboxes(MessageId messageId, Collection<MailboxId> deletedMailboxes) {
        Set<MailboxId> deletedMailboxSet = ImmutableSet.copyOf(deletedMailboxes);
        return mapperFactory.getMessageIdMapper(systemSession)
            .findMailboxesReactive(messageId)
            .all(deletedMailboxSet::contains)
            .onErrorResume(e -> {
                LOGGER.error("Failed to look up the mailboxes of message {}, keeping its encrypted projection", messageId.serialize(), e);
                return Mono.just(false);
            });
    }

    private Mono<Void> deleteProjection(MessageId messageId) {
        return Mono.from(encryptedEmailContentStore.delete(messageId))
            .then()
            .onErrorResume(e -> {
                LOGGER.error("Failed to delete the encrypted projection of message {}", messageId.serialize(), e);
                return Mono.empty();
            });
    }
}
