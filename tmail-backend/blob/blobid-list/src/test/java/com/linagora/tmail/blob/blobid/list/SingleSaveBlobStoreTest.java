package com.linagora.tmail.blob.blobid.list;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.backends.cassandra.components.CassandraModule;
import org.apache.james.backends.cassandra.versions.CassandraSchemaVersionModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.PlainBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdList;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListDAO;
import com.linagora.tmail.blob.blobid.list.cassandra.CassandraBlobIdListModule;

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
    public BlobStoreDAO testee() {
        return blobStoreDAO;
    }

    @Override
    public BlobIdList blobIdList() {
        return cassandraBlobIdList;
    }

    @Override
    public BlobId.Factory blobIdFactory() {
        return new PlainBlobId.Factory();
    }

    @Override
    public BucketName defaultBucketName() {
        return BucketName.DEFAULT;
    }

    @Override
    @Disabled("Not supported")
    public void listBucketsShouldReturnBucketsWithNoBlob() {

    }
}
