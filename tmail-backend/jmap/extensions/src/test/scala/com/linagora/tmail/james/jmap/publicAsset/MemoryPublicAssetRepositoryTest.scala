package com.linagora.tmail.james.jmap.publicAsset

import org.junit.jupiter.api.BeforeEach

class MemoryPublicAssetRepositoryTest extends PublicAssetRepositoryContract {

  var memoryPublicAssetRepository: MemoryPublicAssetRepository = _

  @BeforeEach
  def setup(): Unit = {
    memoryPublicAssetRepository = new MemoryPublicAssetRepository
  }

  override def teste: PublicAssetRepository = memoryPublicAssetRepository
}
