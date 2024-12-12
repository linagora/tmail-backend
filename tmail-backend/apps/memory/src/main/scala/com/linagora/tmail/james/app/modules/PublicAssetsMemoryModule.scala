package com.linagora.tmail.james.app.modules

import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.publicAsset.{MemoryPublicAssetRepository, PublicAssetRepository}

class PublicAssetsMemoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MemoryPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[PublicAssetRepository]).to(classOf[MemoryPublicAssetRepository])
  }
}