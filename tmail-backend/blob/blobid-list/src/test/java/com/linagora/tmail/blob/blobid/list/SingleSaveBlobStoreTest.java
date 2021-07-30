package com.linagora.tmail.blob.blobid.list;

import static org.apache.james.blob.api.BlobStoreDAOFixture.CUSTOM_BUCKET_NAME;
import static org.apache.james.blob.api.BlobStoreDAOFixture.OTHER_TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.SHORT_BYTEARRAY;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BLOB_ID;
import static org.apache.james.blob.api.BlobStoreDAOFixture.TEST_BUCKET_NAME;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdList;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListDAO;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListModule;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class SingleSaveBlobStoreTest implements SingleSaveBlobStoreContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(
            CassandraModule.aggregateModules(CassandraBlobIdListModule.MODULE(),
                    CassandraSchemaVersionModule.MODULE));

    private CassandraBlobIdList cassandraBlobIdList;
    private BlobStoreDAO blobStoreDAO;

    @BeforeEach
    void setUp(CassandraCluster cassandra) {
        CassandraBlobIdListDAO cassandraBlobIdListDAO = new CassandraBlobIdListDAO(cassandra.getConf());
        cassandraBlobIdList = new CassandraBlobIdList(cassandraBlobIdListDAO);
        blobStoreDAO = new SingleSaveBlobStoreDAO(new MemoryBlobStoreDAO(), cassandraBlobIdList, defaultBucketName());
    }

    @Override
    public SingleSaveBlobStoreDAO singleSaveBlobStoreDAO() {
        return new SingleSaveBlobStoreDAO(blobStoreDAO, blobIdList(), defaultBucketName());
    }

    @Override
    public BlobStoreDAO testee() {
        return blobStoreDAO;
    }

    @Override
    public BlobIdList blobIdList() {
        return cassandraBlobIdList;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new HashBlobId.Factory();
    }

    @Override
    public BucketName defaultBucketName() {
        return BucketName.DEFAULT;
    }

    @Override
    @Test
    public void listBucketsShouldReturnAllBucketsInUse() {
        BlobStoreDAO store = testee();

        Mono.from(store.save(TEST_BUCKET_NAME, TEST_BLOB_ID, SHORT_BYTEARRAY)).block();
        Mono.from(store.save(CUSTOM_BUCKET_NAME, OTHER_TEST_BLOB_ID, SHORT_BYTEARRAY)).block();

        assertThat(Flux.from(store.listBuckets()).collectList().block())
            .containsOnly(TEST_BUCKET_NAME, CUSTOM_BUCKET_NAME);
    }

    @Override
    @Disabled("Not supported")
    public void listBucketsShouldReturnBucketsWithNoBlob() {

    }
}
