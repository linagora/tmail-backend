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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.vault.blob;

import static org.apache.james.blob.api.BlobStore.StoragePolicy.LOW_COST;

import java.io.InputStream;

import jakarta.inject.Inject;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.ObjectNotFoundException;
import org.apache.james.core.Username;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.task.Task;
import org.apache.james.vault.DeletedMessage;
import org.apache.james.vault.DeletedMessageContentNotFoundException;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.metadata.DeletedMessageMetadataVault;
import org.apache.james.vault.metadata.DeletedMessageWithStorageInformation;
import org.apache.james.vault.metadata.StorageInformation;
import org.apache.james.vault.search.Query;
import org.reactivestreams.Publisher;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

public class TmailBlobStoreDeletedMessageVault implements DeletedMessageVault {
    private static final String DEFAULT_SINGLE_BUCKET_NAME = "tmail-deleted-message-vault";
    private static final String TMAIL_BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC = "tmailDeletedMessageVault:blobStore:";
    static final String APPEND_METRIC_NAME = TMAIL_BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "append";
    static final String LOAD_MIME_MESSAGE_METRIC_NAME = TMAIL_BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "loadMimeMessage";
    static final String SEARCH_METRIC_NAME = TMAIL_BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "search";
    static final String DELETE_METRIC_NAME = TMAIL_BLOBSTORE_DELETED_MESSAGE_VAULT_METRIC + "delete";

    private final MetricFactory metricFactory;
    private final DeletedMessageMetadataVault messageMetadataVault;
    private final BlobStore blobStore;
    private final BlobStoreDAO blobStoreDAO;
    private final BucketNameGenerator nameGenerator;
    private final BlobIdTimeGenerator blobIdTimeGenerator;

    @Inject
    public TmailBlobStoreDeletedMessageVault(MetricFactory metricFactory, DeletedMessageMetadataVault messageMetadataVault,
                                             BlobStore blobStore, BlobStoreDAO blobStoreDAO, BucketNameGenerator nameGenerator,
                                             BlobIdTimeGenerator blobIdTimeGenerator) {
        this.metricFactory = metricFactory;
        this.messageMetadataVault = messageMetadataVault;
        this.blobStore = blobStore;
        this.blobStoreDAO = blobStoreDAO;
        this.nameGenerator = nameGenerator;
        this.blobIdTimeGenerator = blobIdTimeGenerator;
    }

    @Deprecated
    @VisibleForTesting
    public Publisher<Void> appendV1(DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(mimeMessage);
        BucketName bucketName = nameGenerator.currentBucket();

        return metricFactory.decoratePublisherWithTimerMetric(
            APPEND_METRIC_NAME,
            appendMessageV1(deletedMessage, mimeMessage, bucketName));
    }

    private Mono<Void> appendMessageV1(DeletedMessage deletedMessage, InputStream mimeMessage, BucketName bucketName) {
        return Mono.from(blobStore.save(bucketName, mimeMessage, LOW_COST))
            .map(blobId -> StorageInformation.builder()
                .bucketName(bucketName)
                .blobId(blobId))
            .map(storageInformation -> new DeletedMessageWithStorageInformation(deletedMessage, storageInformation))
            .flatMap(message -> Mono.from(messageMetadataVault.store(message)))
            .then();
    }

    @Override
    public Publisher<Void> append(DeletedMessage deletedMessage, InputStream mimeMessage) {
        Preconditions.checkNotNull(deletedMessage);
        Preconditions.checkNotNull(mimeMessage);
        BucketName bucketName = BucketName.of(DEFAULT_SINGLE_BUCKET_NAME);

        return metricFactory.decoratePublisherWithTimerMetric(
            APPEND_METRIC_NAME,
            appendMessage(deletedMessage, mimeMessage, bucketName));
    }

    private Mono<Void> appendMessage(DeletedMessage deletedMessage, InputStream mimeMessage, BucketName bucketName) {
        return Mono.from(blobStore.save(bucketName, mimeMessage, withTimePrefixBlobId(), LOW_COST))
            .map(blobId -> StorageInformation.builder()
                .bucketName(bucketName)
                .blobId(blobId))
            .map(storageInformation -> new DeletedMessageWithStorageInformation(deletedMessage, storageInformation))
            .flatMap(message -> Mono.from(messageMetadataVault.store(message)))
            .then();
    }

    private BlobStore.BlobIdProvider<InputStream> withTimePrefixBlobId() {
        return data -> Mono.just(Tuples.of(
            blobIdTimeGenerator.currentBlobId(),
            data));
    }

    @Override
    public Publisher<InputStream> loadMimeMessage(Username username, MessageId messageId) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(messageId);

        return metricFactory.decoratePublisherWithTimerMetric(
            LOAD_MIME_MESSAGE_METRIC_NAME,
            Mono.from(messageMetadataVault.retrieveStorageInformation(username, messageId))
                .flatMap(storageInformation -> loadMimeMessage(storageInformation, username, messageId)));
    }

    private Mono<InputStream> loadMimeMessage(StorageInformation storageInformation, Username username, MessageId messageId) {
        return Mono.from(blobStore.readReactive(storageInformation.getBucketName(), storageInformation.getBlobId(), LOW_COST))
            .onErrorResume(
                ObjectNotFoundException.class,
                ex -> Mono.error(new DeletedMessageContentNotFoundException(username, messageId)));
    }

    @Override
    public Publisher<DeletedMessage> search(Username username, Query query) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(query);

        return metricFactory.decoratePublisherWithTimerMetric(
            SEARCH_METRIC_NAME,
            searchOn(username, query));
    }

    private Flux<DeletedMessage> searchOn(Username username, Query query) {
        Flux<DeletedMessage> filterPublisher = Flux.from(messageMetadataVault.listRelatedBuckets())
            .concatMap(bucketName -> messageMetadataVault.listMessages(bucketName, username))
            .map(DeletedMessageWithStorageInformation::getDeletedMessage)
            .filter(query.toPredicate());
        return query.getLimit()
            .map(filterPublisher::take)
            .orElse(filterPublisher);
    }

    @Override
    public Publisher<Void> delete(Username username, MessageId messageId) {
        Preconditions.checkNotNull(username);
        Preconditions.checkNotNull(messageId);

        return metricFactory.decoratePublisherWithTimerMetric(
            DELETE_METRIC_NAME,
            deleteMessage(username, messageId));
    }

    private Mono<Void> deleteMessage(Username username, MessageId messageId) {
        return Mono.from(messageMetadataVault.retrieveStorageInformation(username, messageId))
            .flatMap(storageInformation -> Mono.from(messageMetadataVault.remove(storageInformation.getBucketName(), username, messageId))
                .thenReturn(storageInformation))
            .flatMap(storageInformation -> Mono.from(blobStoreDAO.delete(storageInformation.getBucketName(), storageInformation.getBlobId())));
    }

    @Override
    public Task deleteExpiredMessagesTask() {
        throw new NotImplementedException("not implemented");
    }
}
