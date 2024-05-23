package com.linagora.tmail.james.jmap.publicAsset

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.method.PublicAssetsCapabilityFactory
import org.apache.james.blob.api.BlobReferenceSource
import org.apache.james.jmap.core.CapabilityFactory

class PublicAssetsModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[PublicAssetsCapabilityFactory])

    Multibinder.newSetBinder(binder, classOf[BlobReferenceSource])
      .addBinding()
      .to(classOf[PublicAssetBlobReferenceSource])

    install(new PublicAssetsMemoryModule())
  }
}

class PublicAssetsMemoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MemoryPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[MemoryPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[PublicAssetRepository]).to(classOf[MemoryPublicAssetRepository])
  }
}