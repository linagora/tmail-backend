package com.linagora.tmail.encrypted.cassandra;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.api.MetricableBlobStore;
import org.apache.james.blob.memory.MemoryBlobStoreFactory;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.encrypted.EncryptedEmailContentStore;
import com.linagora.tmail.encrypted.EncryptedEmailContentStoreContract;
import com.linagora.tmail.encrypted.cassandra.table.CassandraEncryptedEmailContentStore;
import com.linagora.tmail.encrypted.cassandra.table.CassandraEncryptedEmailDAO;
import com.linagora.tmail.encrypted.cassandra.table.CassandraEncryptedEmailStoreModule;

public class CassandraEncryptedEmailContentStoreTest implements EncryptedEmailContentStoreContract {
    private final CassandraMessageId.Factory messageIdFactory = new CassandraMessageId.Factory();
    private static final HashBlobId.Factory BLOB_ID_FACTORY = new HashBlobId.Factory();
    private BlobStore blobStore;
    private CassandraEncryptedEmailDAO cassandraEncryptedEmailDAO;

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
        CassandraModule.aggregateModules(CassandraEncryptedEmailStoreModule.MODULE()));

    @RegisterExtension
    MetricableBlobStoreExtension metricsTestExtension = new MetricableBlobStoreExtension();

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

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        blobStore = new MetricableBlobStore(metricsTestExtension.getMetricFactory(), MemoryBlobStoreFactory.builder()
            .blobIdFactory(BLOB_ID_FACTORY)
            .defaultBucketName()
            .passthrough());
        cassandraEncryptedEmailDAO = new CassandraEncryptedEmailDAO(cassandra.getConf(), BLOB_ID_FACTORY);
    }

    @Override
    public EncryptedEmailContentStore testee() {
        return new CassandraEncryptedEmailContentStore(blobStore, bucketName(), BlobStore.StoragePolicy.LOW_COST, cassandraEncryptedEmailDAO);
    }

    @Override
    public MessageId randomMessageId() {
        return messageIdFactory.generate();
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
