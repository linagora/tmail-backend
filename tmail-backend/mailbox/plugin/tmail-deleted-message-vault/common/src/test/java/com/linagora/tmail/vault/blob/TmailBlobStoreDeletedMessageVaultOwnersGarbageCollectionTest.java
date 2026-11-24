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

package com.linagora.tmail.vault.blob;

import static org.apache.james.vault.DeletedMessageFixture.CONTENT;
import static org.apache.james.vault.DeletedMessageFixture.NOW;
import static org.apache.james.vault.search.Query.ALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.utils.UpdatableTickingClock;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.vault.memory.metadata.MemoryDeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.vault.metadata.TmailDeletedMessageMetadataVault;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class TmailBlobStoreDeletedMessageVaultOwnersGarbageCollectionTest {
    private static final Username USER = Username.of("bob@domain.tld");
    private static final Username TEAM_MAILBOX = Username.of("sales@domain.tld");
    private static final Username DELETED_USER = Username.of("alice@domain.tld");

    static class OwnerTrackingMetadataVault implements TmailDeletedMessageMetadataVault {
        private final MemoryDeletedMessageMetadataVault delegate = new MemoryDeletedMessageMetadataVault();
        private final Set<Username> owners = ConcurrentHashMap.newKeySet();

        @Override
        public Publisher<Void> store(DeletedMessageWithStorageInformation deletedMessage) {
            return Mono.fromRunnable(() -> owners.add(deletedMessage.getDeletedMessage().getOwner()))
                .then(Mono.from(delegate.store(deletedMessage)));
        }

        @Override
        public Publisher<Void> removeMetadataRelatedToBucket(BucketName bucketName) {
            return delegate.removeMetadataRelatedToBucket(bucketName);
        }

        @Override
        public Publisher<Void> remove(BucketName bucketName, Username username, MessageId messageId) {
            return delegate.remove(bucketName, username, messageId);
        }

        @Override
        public Publisher<StorageInformation> retrieveStorageInformation(Username username, MessageId messageId) {
            return delegate.retrieveStorageInformation(username, messageId);
        }

        @Override
        public Publisher<DeletedMessageWithStorageInformation> listMessages(BucketName bucketName, Username username) {
            return delegate.listMessages(bucketName, username);
        }

        @Override
        public Publisher<BucketName> listRelatedBuckets() {
            return delegate.listRelatedBuckets();
        }

        @Override
        public Publisher<Username> listOwners(BucketName bucketName) {
            return Flux.fromIterable(ImmutableSet.copyOf(owners));
        }
    }

    private TmailBlobStoreDeletedMessageVault messageVault;
    private UpdatableTickingClock clock;

    @BeforeEach
    void setUp() throws Exception {
        clock = new UpdatableTickingClock(NOW.toInstant());
        DomainList domainList = mock(DomainList.class);
        Mockito.when(domainList.containsDomain(any())).thenReturn(true);
        MemoryUsersRepository usersRepository = MemoryUsersRepository.withVirtualHosting(domainList);
        usersRepository.addUser(USER, "123456");

        messageVault = new TmailBlobStoreDeletedMessageVault(new RecordingMetricFactory(), new OwnerTrackingMetadataVault(),
            new MemoryBlobStoreDAO(), new BucketNameGenerator(clock), clock,
            VaultConfiguration.ENABLED_DEFAULT, usersRepository);
    }

    @Test
    void deleteExpiredMessagesTaskShouldPurgeExpiredMessagesOfUsers() throws Exception {
        appendExpiredMessage(USER, 1);

        messageVault.deleteExpiredMessagesTask().run();

        assertThat(Flux.from(messageVault.search(USER, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    void deleteExpiredMessagesTaskShouldPurgeExpiredMessagesOfOwnersThatAreNotUsers() throws Exception {
        appendExpiredMessage(TEAM_MAILBOX, 1);

        messageVault.deleteExpiredMessagesTask().run();

        assertThat(Flux.from(messageVault.search(TEAM_MAILBOX, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    void deleteExpiredMessagesTaskShouldPurgeExpiredMessagesOfDeletedUsers() throws Exception {
        appendExpiredMessage(DELETED_USER, 1);

        messageVault.deleteExpiredMessagesTask().run();

        assertThat(Flux.from(messageVault.search(DELETED_USER, ALL)).collectList().block())
            .isEmpty();
    }

    @Test
    void deleteExpiredMessagesTaskShouldKeepNonExpiredMessagesOfOwnersThatAreNotUsers() throws Exception {
        DeletedMessage recentMessage = deletedMessage(TEAM_MAILBOX, 2, NOW.minusDays(1));
        Mono.from(messageVault.append(recentMessage, new ByteArrayInputStream(CONTENT))).block();

        messageVault.deleteExpiredMessagesTask().run();

        assertThat(Flux.from(messageVault.search(TEAM_MAILBOX, ALL)).collectList().block())
            .containsOnly(recentMessage);
    }

    private void appendExpiredMessage(Username owner, long messageId) {
        DeletedMessage expiredMessage = deletedMessage(owner, messageId, NOW.minusYears(2));
        Mono.from(messageVault.append(expiredMessage, new ByteArrayInputStream(CONTENT))).block();
    }

    private DeletedMessage deletedMessage(Username owner, long messageId, ZonedDateTime deletionDate) {
        return DeletedMessage.builder()
            .messageId(InMemoryMessageId.of(messageId))
            .originMailboxes(InMemoryId.of(1))
            .user(owner)
            .deliveryDate(deletionDate)
            .deletionDate(deletionDate)
            .sender(MaybeSender.nullSender())
            .recipients()
            .hasAttachment(false)
            .size(CONTENT.length)
            .build();
    }
}
