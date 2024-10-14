package com.linagora.tmail.blob.secondaryblobstore;

import static com.linagora.tmail.blob.secondaryblobstore.ObjectStorageIdentity.PRIMARY;
import static com.linagora.tmail.blob.secondaryblobstore.ObjectStorageIdentity.SECONDARY;

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

    public FailedBlobOperationListener(BlobStoreDAO primaryBlobStoreDAO,
                                       BlobStoreDAO secondaryBlobStoreDAO) {
        this.primaryBlobStoreDAO = primaryBlobStoreDAO;
        this.secondaryBlobStoreDAO = secondaryBlobStoreDAO;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        FailedBlobEvents.BlobEvent blobEvent = (FailedBlobEvents.BlobEvent) event;
        ObjectStorageIdentity failedObjectStorage = blobEvent.getFailedObjectStorage();
        BlobStoreDAO successfulBlobStoreDAO = successfulBlobStoreDAO(failedObjectStorage);
        BlobStoreDAO failedBlobStoreDAO = failedBlobStoreDAO(failedObjectStorage);

        if (blobEvent instanceof FailedBlobEvents.BlobAddition blobAdditionEvent) {
            return Mono.from(copyBlob(blobAdditionEvent.bucketName(), blobAdditionEvent.blobId(), successfulBlobStoreDAO, failedBlobStoreDAO));
        }

        if (blobEvent instanceof FailedBlobEvents.BlobsDeletion blobsDeletionEvent) {
            return Mono.from(failedBlobStoreDAO.delete(blobsDeletionEvent.bucketName(), blobsDeletionEvent.blobIds()));
        }

        if (blobEvent instanceof FailedBlobEvents.BucketDeletion bucketDeletionEvent) {
            return Mono.from(failedBlobStoreDAO.deleteBucket(bucketDeletionEvent.bucketName()));
        }
        return Mono.empty();
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

    private Mono<Void> copyBlob(BucketName bucketName, BlobId blobId, BlobStoreDAO fromBlobStore, BlobStoreDAO toBlobStore) {
        return Mono.from(fromBlobStore.readReactive(bucketName, blobId))
            .flatMap(inputStream -> Mono.from(toBlobStore.save(bucketName, blobId, inputStream)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private BlobStoreDAO successfulBlobStoreDAO(ObjectStorageIdentity failedObjectStorage) {
        if (SECONDARY.equals(failedObjectStorage)) {
            return primaryBlobStoreDAO;
        } else {
            return secondaryBlobStoreDAO;
        }
    }

    private BlobStoreDAO failedBlobStoreDAO(ObjectStorageIdentity failedObjectStorage) {
        if (PRIMARY.equals(failedObjectStorage)) {
            return primaryBlobStoreDAO;
        } else {
            return secondaryBlobStoreDAO;
        }
    }
}
