package com.linagora.tmail.blob.secondaryblobstore;

import java.util.Collection;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.core.Username;
import org.apache.james.events.Event;

public interface FailedBlobEvents {
    interface BlobEvent extends Event {
        Username USERNAME = Username.of("SecondaryBlobStore");

        ObjectStorageIdentity getFailedObjectStorage();
    }

    record BlobAddition(EventId eventId, BucketName bucketName, BlobId blobId, ObjectStorageIdentity failedObjectStorage) implements BlobEvent {
        @Override
        public Username getUsername() {
            return USERNAME;
        }

        @Override
        public boolean isNoop() {
            return false;
        }

        @Override
        public EventId getEventId() {
            return eventId;
        }

        @Override
        public ObjectStorageIdentity getFailedObjectStorage() {
            return failedObjectStorage;
        }
    }

    record BlobsDeletion(EventId eventId, BucketName bucketName, Collection<BlobId> blobIds, ObjectStorageIdentity failedObjectStorage) implements BlobEvent {
        @Override
        public Username getUsername() {
            return USERNAME;
        }

        @Override
        public boolean isNoop() {
            return false;
        }

        @Override
        public EventId getEventId() {
            return eventId;
        }

        @Override
        public ObjectStorageIdentity getFailedObjectStorage() {
            return failedObjectStorage;
        }
    }

    record BucketDeletion(EventId eventId, BucketName bucketName, ObjectStorageIdentity failedObjectStorage) implements BlobEvent {
        @Override
        public Username getUsername() {
            return USERNAME;
        }

        @Override
        public boolean isNoop() {
            return false;
        }

        @Override
        public EventId getEventId() {
            return eventId;
        }

        @Override
        public ObjectStorageIdentity getFailedObjectStorage() {
            return failedObjectStorage;
        }
    }
}
