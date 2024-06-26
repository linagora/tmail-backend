package com.linagora.tmail.james.jmap.publicAsset;

import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

class PostgresPublicAssetRepositoryTest implements PublicAssetRepositoryContract {
    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withRowLevelSecurity(PublicAssetTable.MODULE());

    private PublicAssetRepository publicAssetRepository;

    @BeforeEach
    void setup() {
        publicAssetRepository = new PostgresPublicAssetRepository(postgresExtension.getExecutorFactory(),
            blobIdFactory(),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory()),
            PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX(),
            postgresExtension.getByPassRLSPostgresExecutor());
    }

    @Override
    public PublicAssetRepository teste() {
        return publicAssetRepository;
    }
}
