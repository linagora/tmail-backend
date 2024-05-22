package com.linagora.tmail.james.jmap.publicAsset

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
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
  }
}