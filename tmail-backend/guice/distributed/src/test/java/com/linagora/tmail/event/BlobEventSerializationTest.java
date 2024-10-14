package com.linagora.tmail.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.events.Event;
import org.apache.james.server.blob.deduplication.GenerationAwareBlobId;
import org.apache.james.utils.UpdatableTickingClock;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.linagora.tmail.blob.secondaryblobstore.FailedBlobEvents;
import com.linagora.tmail.blob.secondaryblobstore.ObjectStorageIdentity;

public class BlobEventSerializationTest {
    static final Instant NOW = Instant.parse("2021-08-19T10:15:30.00Z");
    static final BlobId.Factory BLOB_ID_FACTORY = new GenerationAwareBlobId.Factory(new UpdatableTickingClock(NOW),
        new PlainBlobId.Factory(),
        GenerationAwareBlobId.Configuration.DEFAULT);
    static final Event.EventId EVENT_ID = Event.EventId.of("6e0dd59d-660e-4d9b-b22f-0354479f47b4");
    static final BucketName BUCKET_NAME = BucketName.of("bucket-1");
    static final BlobId BLOB_ID = BLOB_ID_FACTORY.of("blob-id-1");
    static final Event BLOB_ADDTITION_EVENT = new FailedBlobEvents.BlobAddition(EVENT_ID, BUCKET_NAME, BLOB_ID, ObjectStorageIdentity.PRIMARY);
    static final Event BLOBS_DELETION_EVENT = new FailedBlobEvents.BlobsDeletion(EVENT_ID, BUCKET_NAME, ImmutableList.of(BLOB_ID), ObjectStorageIdentity.PRIMARY);
    static final Event BUCKET_DELETION_EVENT = new FailedBlobEvents.BucketDeletion(EVENT_ID, BUCKET_NAME, ObjectStorageIdentity.PRIMARY);
    static final String BlOB_ADDITION_JSON = "{" +
        "\"type\":\"TmailEventSerializer$BlobAdditionDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"secondaryblobstore\"," +
        "\"bucketName\":\"bucket-1\"," +
        "\"blobId\":\"1_628_blob-id-1\"," +
        "\"failedObjectStorage\":\"PRIMARY\"" +
        "}";
    static final String BlOBS_DELETION_JSON = "{" +
        "\"type\":\"TmailEventSerializer$BlobsDeletionDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"secondaryblobstore\"," +
        "\"bucketName\":\"bucket-1\"," +
        "\"blobIds\":[\"1_628_blob-id-1\"]," +
        "\"failedObjectStorage\":\"PRIMARY\"" +
        "}";
    static final String BUCKET_DELETION_JSON = "{" +
        "\"type\":\"TmailEventSerializer$BucketDeletionDTO\"," +
        "\"eventId\":\"6e0dd59d-660e-4d9b-b22f-0354479f47b4\"," +
        "\"username\":\"secondaryblobstore\"," +
        "\"bucketName\":\"bucket-1\"," +
        "\"failedObjectStorage\":\"PRIMARY\"" +
        "}";

    private final TmailEventSerializer tmailEventSerializer = new TmailEventSerializer(BLOB_ID_FACTORY);

    @Test
    void blobAdditionEventShouldBeWellSerialized() {
        String json = tmailEventSerializer.toJson(BLOB_ADDTITION_EVENT);
        assertThat(json).isEqualTo(BlOB_ADDITION_JSON);
    }

    @Test
    void blobAdditionEventShouldBeWellDeserialized() {
        Event event = tmailEventSerializer.asEvent(BlOB_ADDITION_JSON);
        assertThat(event).isEqualTo(BLOB_ADDTITION_EVENT);
    }

    @Test
    void blobsDeletionEventShouldBeWellSerialized() {
        String json = tmailEventSerializer.toJson(BLOBS_DELETION_EVENT);
        assertThat(json).isEqualTo(BlOBS_DELETION_JSON);
    }

    @Test
    void blobsDeletionEventShouldBeWellDeserialized() {
        Event event = tmailEventSerializer.asEvent(BlOBS_DELETION_JSON);
        assertThat(event).isEqualTo(BLOBS_DELETION_EVENT);
    }

    @Test
    void bucketDeletionEventShouldBeWellSerialized() {
        String json = tmailEventSerializer.toJson(BUCKET_DELETION_EVENT);
        assertThat(json).isEqualTo(BUCKET_DELETION_JSON);
    }

    @Test
    void bucketDeletionEventShouldBeWellDeserialized() {
        Event event = tmailEventSerializer.asEvent(BUCKET_DELETION_JSON);
        assertThat(event).isEqualTo(BUCKET_DELETION_EVENT);
    }
}
