package com.linagora.tmail.james.jmap.publicAsset

import org.apache.james.blob.api.BlobId
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

trait PublicAssetRepository {
  def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage]

  def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): Publisher[Void]

  def remove(username: Username, id: PublicAssetId): Publisher[Void]

  def revoke(username: Username): Publisher[Void]

  def get(username: Username, ids: Set[PublicAssetId]): Publisher[PublicAssetStorage]

  def get(username: Username, id: PublicAssetId): Publisher[PublicAssetStorage] = get(username, Set(id))

  def list(username: Username): Publisher[PublicAssetStorage]

  def listPublicAssetMetaDataOrderByIdAsc(username: Username): Publisher[PublicAssetMetadata]

  def listAllBlobIds(): Publisher[BlobId]

  def updateIdentityIds(username: Username, id: PublicAssetId, identityIdsToAdd: Seq[IdentityId], identityIdsToRemove: Seq[IdentityId]): Publisher[Void] =
    SMono(get(username, id))
      .map(publicAsset => (publicAsset.identityIds.toSet ++ identityIdsToAdd.toSet) -- identityIdsToRemove.toSet)
      .flatMap(identityIds => SMono(update(username, id, identityIds)))

  def getTotalSize(username: Username): Publisher[Long]
}