package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Scopes, Singleton}
import com.linagora.tmail.james.jmap.method.{PublicAssetSetMethod, PublicAssetsCapabilityFactory}
import jakarta.inject.Named
import org.apache.commons.configuration2.Configuration
import org.apache.james.blob.api.BlobReferenceSource
import org.apache.james.jmap.core.CapabilityFactory
import org.apache.james.jmap.method.Method

class PublicAssetsModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder(), classOf[CapabilityFactory])
      .addBinding()
      .to(classOf[PublicAssetsCapabilityFactory])
    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[PublicAssetSetMethod])
    Multibinder.newSetBinder(binder, classOf[BlobReferenceSource])
      .addBinding()
      .to(classOf[PublicAssetBlobReferenceSource])
  }

  @Provides
  @Singleton
  @Named("publicAssetUriPrefix")
  def providePublicAssetUriPrefix(@Named("jmap") jmapConfiguration: Configuration): URI = {
    PublicAssetURIPrefix.fromConfiguration(jmapConfiguration).fold(throw _, identity)
  }
}

class PublicAssetsMemoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MemoryPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[PublicAssetRepository]).to(classOf[MemoryPublicAssetRepository])
  }
}