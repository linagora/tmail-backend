package com.linagora.tmail.james.jmap.publicAsset

import jakarta.inject.Inject
import org.apache.james.blob.api.{BlobId, BlobReferenceSource}
import org.reactivestreams.Publisher

class PublicAssetBlobReferenceSource @Inject()(repository: PublicAssetRepository) extends BlobReferenceSource {

  override def listReferencedBlobs(): Publisher[BlobId] = repository.listAllBlobIds()
}
