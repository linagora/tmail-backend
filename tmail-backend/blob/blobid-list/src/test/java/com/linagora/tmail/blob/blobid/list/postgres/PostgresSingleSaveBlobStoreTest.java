package com.linagora.tmail.blob.blobid.list.postgres;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.backends.postgres.PostgresModule;
import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStoreDAO;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.api.HashBlobId;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.blob.blobid.list.BlobIdList;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreContract;
import com.linagora.tmail.blob.blobid.list.SingleSaveBlobStoreDAO;

public class PostgresSingleSaveBlobStoreTest implements SingleSaveBlobStoreContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(
        PostgresModule.aggregateModules(PostgresBlobIdListModule.MODULE()));

    private PostgresBlobIdList postgresBlobIdList;
    private BlobStoreDAO blobStoreDAO;

    @BeforeEach
    void setUp() {
        postgresBlobIdList = new PostgresBlobIdList(new PostgresBlobIdListDAO(postgresExtension.getDefaultPostgresExecutor()));
        blobStoreDAO = new SingleSaveBlobStoreDAO(new MemoryBlobStoreDAO(), postgresBlobIdList, defaultBucketName());
    }

    @Override
    public BlobStoreDAO testee() {
        return blobStoreDAO;
    }

    @Override
    public BlobIdList blobIdList() {
        return postgresBlobIdList;
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
    @Disabled("Not supported")
    public void listBucketsShouldReturnBucketsWithNoBlob() {

    }
}
