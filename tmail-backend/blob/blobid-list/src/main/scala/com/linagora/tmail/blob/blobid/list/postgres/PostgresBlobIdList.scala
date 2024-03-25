package com.linagora.tmail.blob.blobid.list.postgres

import java.lang

import com.linagora.tmail.blob.blobid.list.BlobIdList
import javax.inject.Inject
import org.apache.james.blob.api.BlobId
import org.reactivestreams.Publisher

class PostgresBlobIdList @Inject()(postgresBlobIdListDAO: PostgresBlobIdListDAO) extends BlobIdList {
  override def isStored(blobId: BlobId): Publisher[lang.Boolean] =
    postgresBlobIdListDAO.isStored(blobId)

  override def store(blobId: BlobId): Publisher[Unit] =
    postgresBlobIdListDAO.insert(blobId)

  override def remove(blobId: BlobId): Publisher[Unit] =
    postgresBlobIdListDAO.remove(blobId)
}
