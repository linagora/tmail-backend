package com.linagora.tmail.james.jmap.publicAsset

import com.google.inject.{AbstractModule, Scopes}

class PublicAssetsMemoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MemoryPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[PublicAssetRepository]).to(classOf[MemoryPublicAssetRepository])
  }
}