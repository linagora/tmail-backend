package com.linagora.tmail.james.jmap.publicAsset;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration;

class CassandraPublicAssetServiceTest implements PublicAssetServiceContract {

    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraPublicAssetTable.MODULE());

    private PublicAssetRepository publicAssetRepository;

    private PublicAssetSetService publicAssetSetService;

    @BeforeEach
    void setup(CassandraCluster cassandra) {
        publicAssetRepository = new CassandraPublicAssetRepository(
            new CassandraPublicAssetDAO(cassandra.getConf(), blobIdFactory()),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory()),
            new JMAPExtensionConfiguration(JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT()),
            PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX(),
            PublicAssetServiceContract.CLOCK());

        publicAssetSetService = new PublicAssetSetService(PublicAssetServiceContract.identityRepository(), publicAssetRepository);
    }

    @Override
    public PublicAssetRepository publicAssetRepository() {
        return publicAssetRepository;
    }

    @Override
    public PublicAssetSetService testee() {
        return publicAssetSetService;
    }
}
