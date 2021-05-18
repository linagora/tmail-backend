package com.linagora.tmail.blob.blobid.list

import org.apache.james.blob.api.BlobId
import org.reactivestreams.Publisher

trait BlobIdList {
  def isStored(blobId: BlobId): Publisher[Boolean]

  def store(blobId: BlobId): Publisher[Unit]

  def remove(blobId: BlobId): Publisher[Unit]
}
