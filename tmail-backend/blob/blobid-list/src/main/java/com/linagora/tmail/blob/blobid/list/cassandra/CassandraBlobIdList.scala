package com.linagora.tmail.blob.blobid.list.cassandra

import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.blob.blobid.list.BlobIdList
import org.apache.james.blob.api.BlobId
import org.reactivestreams.Publisher

import javax.inject.Inject

case class BlobIdListCassandraModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraBlobIdListDAO]).in(Scopes.SINGLETON)
    bind(classOf[CassandraBlobIdList]).in(Scopes.SINGLETON)

    bind(classOf[BlobIdList]).to(classOf[CassandraBlobIdList])
  }
}

class CassandraBlobIdList @Inject()(cassandraBlobIdListDAO: CassandraBlobIdListDAO) extends BlobIdList {

  override def isStored(blobId: BlobId): Publisher[java.lang.Boolean] =
    cassandraBlobIdListDAO.isStored(blobId)

  override def store(blobId: BlobId): Publisher[Unit] =
    cassandraBlobIdListDAO.insert(blobId)

  override def remove(blobId: BlobId): Publisher[Unit] =
    cassandraBlobIdListDAO.remove(blobId)
}
