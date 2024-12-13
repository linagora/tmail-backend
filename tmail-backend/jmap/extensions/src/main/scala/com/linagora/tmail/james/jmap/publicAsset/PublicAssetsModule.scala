package com.linagora.tmail.james.jmap.publicAsset

import java.net.URI

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Provides, Scopes, Singleton}
import com.linagora.tmail.james.jmap.method.{PublicAssetGetMethod, PublicAssetSetMethod, PublicAssetsCapabilityFactory}
import jakarta.inject.Named
import org.apache.james.blob.api.BlobReferenceSource
import org.apache.james.jmap.JMAPRoutes
import org.apache.james.jmap.core.{CapabilityFactory, JmapRfc8621Configuration}
import org.apache.james.jmap.method.Method
import org.apache.james.user.api.DeleteUserDataTaskStep

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

    Multibinder.newSetBinder(binder(), classOf[Method])
      .addBinding()
      .to(classOf[PublicAssetGetMethod])

    val routes: Multibinder[JMAPRoutes] = Multibinder.newSetBinder(binder, classOf[JMAPRoutes])
    routes.addBinding().to(classOf[PublicAssetRoutes])

    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[PublicAssetDeletionTaskStep])
  }

  @Provides
  @Singleton
  @Named("publicAssetUriPrefix")
  def providePublicAssetUriPrefix(jmapConfiguration: JmapRfc8621Configuration): URI = {
    PublicAssetURIPrefix.fromConfiguration(jmapConfiguration).fold(throw _, identity)
  }
}