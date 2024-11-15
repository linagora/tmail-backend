package com.linagora.tmail.blob.secondaryblobstore;

import static com.linagora.tmail.blob.secondaryblobstore.SecondaryBlobStoreDAO.withSuffix;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.events.Event;
import org.apache.james.events.Group;
import org.reactivestreams.Publisher;

import com.linagora.tmail.common.event.TmailReactiveGroupEventListener;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class FailedBlobOperationListener implements TmailReactiveGroupEventListener {
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
            case PRIMARY -> readFromSecondaryAndSaveToPrimary(blobAdditionEvent.bucketName(), blobAdditionEvent.blobId());
            case SECONDARY -> readFromPrimaryAndSaveToSecondary(blobAdditionEvent.bucketName(), blobAdditionEvent.blobId());
        };
    }

    private Mono<Void> readFromSecondaryAndSaveToPrimary(BucketName bucketName, BlobId blobId) {
        return Mono.from(secondaryBlobStoreDAO.readReactive(withSuffix(bucketName, secondaryBucketSuffix), blobId))
            .flatMap(inputStream -> Mono.from(primaryBlobStoreDAO.save(bucketName, blobId, inputStream)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> readFromPrimaryAndSaveToSecondary(BucketName bucketName, BlobId blobId) {
        return Mono.from(primaryBlobStoreDAO.readReactive(bucketName, blobId))
            .flatMap(inputStream -> Mono.from(secondaryBlobStoreDAO.save(withSuffix(bucketName, secondaryBucketSuffix), blobId, inputStream)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<Void> handleFailedBlobsDeletionEvent(FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
        return switch (blobsDeletionEvent.failedObjectStorage()) {
            case PRIMARY -> deleteBlobsFromPrimaryBucket(blobsDeletionEvent);
            case SECONDARY -> deleteBlobsFromSecondaryBucket(blobsDeletionEvent);
        };
    }

    private Mono<Void> deleteBlobsFromPrimaryBucket(FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
        return Mono.from(primaryBlobStoreDAO.delete(blobsDeletionEvent.bucketName(), blobsDeletionEvent.blobIds()));
    }

    private Mono<Void> deleteBlobsFromSecondaryBucket(FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
        return Mono.from(secondaryBlobStoreDAO.delete(withSuffix(blobsDeletionEvent.bucketName(), secondaryBucketSuffix), blobsDeletionEvent.blobIds()));
    }

    private Publisher<Void> handleFailedBucketDeletionEvent(FailedBlobEvents.BucketDeletion bucketDeletionEvent) {
        return switch (bucketDeletionEvent.failedObjectStorage()) {
            case PRIMARY -> primaryBlobStoreDAO.deleteBucket(bucketDeletionEvent.bucketName());
            case SECONDARY -> secondaryBlobStoreDAO.deleteBucket(withSuffix(bucketDeletionEvent.bucketName(), secondaryBucketSuffix));
        };
    }
}
