package com.linagora.tmail.blob.blobid.list.postgres

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.blob.blobid.list.BlobIdList
import org.apache.james.backends.postgres.PostgresModule

class PostgresBlobIdListGuiceModule extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder, classOf[PostgresModule])
      .addBinding
      .toInstance(PostgresBlobIdListModule.MODULE)
    
    bind(classOf[PostgresBlobIdListDAO]).in(Scopes.SINGLETON)
    bind(classOf[PostgresBlobIdList]).in(Scopes.SINGLETON)

    bind(classOf[BlobIdList]).to(classOf[PostgresBlobIdList])
  }
}

class PostgresSingleSaveBlobStoreModule extends AbstractModule {
  override def configure(): Unit = {
    install(new PostgresBlobIdListGuiceModule)
  }
}
