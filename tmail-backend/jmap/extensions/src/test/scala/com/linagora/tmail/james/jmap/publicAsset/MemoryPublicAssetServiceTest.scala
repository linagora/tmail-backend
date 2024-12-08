package com.linagora.tmail.james.jmap.publicAsset

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetServiceContract.identityRepository
import org.apache.james.blob.api.BucketName
import org.apache.james.blob.memory.MemoryBlobStoreDAO
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore
import org.junit.jupiter.api.BeforeEach

class MemoryPublicAssetServiceTest extends PublicAssetServiceContract {
  var publicAssetSetService: PublicAssetSetService = _

  var memoryPublicAssetRepository: MemoryPublicAssetRepository = _

  @BeforeEach
  def setup(): Unit = {
    val blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO, BucketName.DEFAULT, blobIdFactory)
    memoryPublicAssetRepository = new MemoryPublicAssetRepository(blobStore, JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, PUBLIC_ASSET_URI_PREFIX)
    publicAssetSetService = new PublicAssetSetService(identityRepository, memoryPublicAssetRepository, JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT)
  }

  override def testee: PublicAssetSetService = publicAssetSetService

  override def publicAssetRepository: PublicAssetRepository = memoryPublicAssetRepository
}
