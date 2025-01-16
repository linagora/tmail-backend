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

package com.linagora.tmail.blob.secondaryblobstore;

import java.util.stream.Collectors;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class FailedBlobOperationListener implements EventListener.ReactiveGroupEventListener {
    public static final Logger LOGGER = LoggerFactory.getLogger(FailedBlobOperationListener.class);

    public static class FailedBlobOperationListenerGroup extends Group {
    }

    private final BlobStoreDAO primaryBlobStoreDAO;
    private final BlobStoreDAO secondaryBlobStoreDAO;
    private final String secondaryBucketSuffix;

    public FailedBlobOperationListener(BlobStoreDAO primaryBlobStoreDAO,
                                       BlobStoreDAO secondaryBlobStoreDAO,
                                       String secondaryBucketSuffix) {
        this.primaryBlobStoreDAO = primaryBlobStoreDAO;
        this.secondaryBlobStoreDAO = secondaryBlobStoreDAO;
        this.secondaryBucketSuffix = secondaryBucketSuffix;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        return switch (event) {
            case FailedBlobEvents.BlobAddition blobAdditionEvent -> handleFailedBlobsAdditionEvent(blobAdditionEvent);
            case FailedBlobEvents.BlobsDeletion blobsDeletionEvent -> handleFailedBlobsDeletionEvent(blobsDeletionEvent);
            case FailedBlobEvents.BucketDeletion bucketDeletionEvent -> handleFailedBucketDeletionEvent(bucketDeletionEvent);
            default -> Mono.empty();
        };
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof FailedBlobEvents.BlobAddition
            || event instanceof FailedBlobEvents.BlobsDeletion
            || event instanceof FailedBlobEvents.BucketDeletion;
    }

    @Override
    public Group getDefaultGroup() {
        return new FailedBlobOperationListenerGroup();
    }

    private Mono<Void> handleFailedBlobsAdditionEvent(FailedBlobEvents.BlobAddition blobAdditionEvent) {
        return switch (blobAdditionEvent.failedObjectStorage()) {
            case PRIMARY -> readFromSecondaryAndSaveToPrimary(blobAdditionEvent.bucketName(), blobAdditionEvent.blobId())
                .doOnSuccess(any -> LOGGER.info("Saved {} in secondary reading primary S3", blobAdditionEvent.blobId().asString()));
            case SECONDARY -> readFromPrimaryAndSaveToSecondary(blobAdditionEvent.bucketName(), blobAdditionEvent.blobId())
                .doOnSuccess(any -> LOGGER.info("Saved {} in primary reading secondary S3", blobAdditionEvent.blobId().asString()));
        };
    }

    private Mono<Void> readFromSecondaryAndSaveToPrimary(BucketName bucketName, BlobId blobId) {
        return Mono.from(secondaryBlobStoreDAO.readReactive(withSuffix(bucketName), blobId))
            .flatMap(inputStream -> Mono.from(primaryBlobStoreDAO.save(bucketName, blobId, inputStream)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> readFromPrimaryAndSaveToSecondary(BucketName bucketName, BlobId blobId) {
        return Mono.from(primaryBlobStoreDAO.readReactive(bucketName, blobId))
            .flatMap(inputStream -> Mono.from(secondaryBlobStoreDAO.save(withSuffix(bucketName), blobId, inputStream)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> handleFailedBlobsDeletionEvent(FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
        return switch (blobsDeletionEvent.failedObjectStorage()) {
            case PRIMARY -> deleteBlobsFromPrimaryBucket(blobsDeletionEvent)
                .doOnSuccess(any -> LOGGER.info("Delete {} in primary", blobsDeletionEvent.blobIds().stream().map(BlobId::asString).collect(Collectors.toList())));
            case SECONDARY -> deleteBlobsFromSecondaryBucket(blobsDeletionEvent)
                .doOnSuccess(any -> LOGGER.info("Delete {} in secondary", blobsDeletionEvent.blobIds().stream().map(BlobId::asString).collect(Collectors.toList())));
        };
    }

    private Mono<Void> deleteBlobsFromPrimaryBucket(FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
        return Mono.from(primaryBlobStoreDAO.delete(blobsDeletionEvent.bucketName(), blobsDeletionEvent.blobIds()));
    }

    private Mono<Void> deleteBlobsFromSecondaryBucket(FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
        return Mono.from(secondaryBlobStoreDAO.delete(withSuffix(blobsDeletionEvent.bucketName()), blobsDeletionEvent.blobIds()));
    }

    private Publisher<Void> handleFailedBucketDeletionEvent(FailedBlobEvents.BucketDeletion bucketDeletionEvent) {
        return switch (bucketDeletionEvent.failedObjectStorage()) {
            case PRIMARY -> Mono.from(primaryBlobStoreDAO.deleteBucket(bucketDeletionEvent.bucketName()))
                .doOnSuccess(any -> LOGGER.info("Delete {} in primary", bucketDeletionEvent.bucketName().asString()));
            case SECONDARY -> Mono.from(secondaryBlobStoreDAO.deleteBucket(withSuffix(bucketDeletionEvent.bucketName())))
                .doOnSuccess(any -> LOGGER.info("Delete {} in primary", bucketDeletionEvent.bucketName().asString()));
        };
    }

    private BucketName withSuffix(BucketName bucketName) {
        return BucketName.of(bucketName.asString() + secondaryBucketSuffix);
    }
}
