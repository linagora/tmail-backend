package com.linagora.tmail.james.jmap.publicAsset;

import org.apache.james.backends.cassandra.CassandraCluster;
import org.apache.james.backends.cassandra.CassandraClusterExtension;
import org.apache.james.blob.api.BucketName;
import org.apache.james.blob.memory.MemoryBlobStoreDAO;
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore;
import org.apache.james.util.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.linagora.tmail.james.jmap.PublicAssetTotalSizeLimit;

class CassandraPublicAssetRepositoryTest implements PublicAssetRepositoryContract {
    @RegisterExtension
    static CassandraClusterExtension cassandraCluster = new CassandraClusterExtension(CassandraPublicAssetTable.MODULE());

    private PublicAssetRepository publicAssetRepository;

    @BeforeEach
    void setup(CassandraCluster cassandra) {
        PublicAssetTotalSizeLimit publicAssetTotalSizeLimit =
            PublicAssetTotalSizeLimit.of(Size.of(20L, Size.Unit.M)).get();
        publicAssetRepository = new CassandraPublicAssetRepository(
            new CassandraPublicAssetDAO(cassandra.getConf(), blobIdFactory()),
            new DeDuplicationBlobStore(new MemoryBlobStoreDAO(), BucketName.DEFAULT, blobIdFactory()),
            publicAssetTotalSizeLimit,
            PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX());
    }

    @Override
    public PublicAssetRepository teste() {
        return publicAssetRepository;
    }
}
