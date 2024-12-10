package com.linagora.tmail.james.jmap.publicAsset

import java.time.Clock

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import com.linagora.tmail.james.jmap.publicAsset.PublicAssetRepositoryContract.PUBLIC_ASSET_URI_PREFIX
import org.apache.james.blob.api.BucketName
import org.apache.james.blob.memory.MemoryBlobStoreDAO
import org.apache.james.server.blob.deduplication.DeDuplicationBlobStore
import org.junit.jupiter.api.BeforeEach

class MemoryPublicAssetRepositoryTest extends PublicAssetRepositoryContract {

  var memoryPublicAssetRepository: MemoryPublicAssetRepository = _

  @BeforeEach
  def setup(): Unit = {

    val blobStore = new DeDuplicationBlobStore(new MemoryBlobStoreDAO, BucketName.DEFAULT, blobIdFactory)
    memoryPublicAssetRepository = new MemoryPublicAssetRepository(blobStore, JMAPExtensionConfiguration.PUBLIC_ASSET_TOTAL_SIZE_LIMIT_DEFAULT, PUBLIC_ASSET_URI_PREFIX)
  }

  override def teste: PublicAssetRepository = memoryPublicAssetRepository
}
