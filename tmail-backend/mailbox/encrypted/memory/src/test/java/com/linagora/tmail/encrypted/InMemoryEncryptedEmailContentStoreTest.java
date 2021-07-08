package com.linagora.tmail.encrypted;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

public class InMemoryEncryptedEmailContentStoreTest implements EncryptedEmailContentStoreContract {
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private BlobStore blobStore;
    private InMemoryEncryptedEmailContentStore inMemoryEncryptedEmailContentStore;

    static class MetricableBlobStoreExtension implements BeforeEachCallback {
        private RecordingMetricFactory metricFactory;

        @Override
        public void beforeEach(ExtensionContext extensionContext) {
            this.metricFactory = new RecordingMetricFactory();
        }

        public RecordingMetricFactory getMetricFactory() {
            return metricFactory;
        }
    }

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();

    @BeforeEach
    void setUp() {
        blobStore = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), MemoryBlobStoreFactory.builder()
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .passthrough());
        inMemoryEncryptedEmailContentStore = new InMemoryEncryptedEmailContentStore(blobStore);
    }

    @Override
    public EncryptedEmailContentStore testee() {
        return inMemoryEncryptedEmailContentStore;
    }

    @Override
    public MessageId randomMessageId() {
        return InMemoryMessageId.of(ThreadLocalRandom.current().nextInt(100000) + 100);
    }

    @Override
    public BlobStore blobStore() {
        return blobStore;
    }

    @Override
    public BucketName bucketName() {
        return blobStore.getDefaultBucketName();
    }
}
