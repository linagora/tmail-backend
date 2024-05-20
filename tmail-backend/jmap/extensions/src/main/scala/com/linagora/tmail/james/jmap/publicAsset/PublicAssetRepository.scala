package com.linagora.tmail.james.jmap.publicAsset

import com.google.common.collect.{HashBasedTable, Table, Tables}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

trait PublicAssetRepository {
  def create(username: Username, creationRequest: PublicAsset): Publisher[PublicAsset]

  def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): Publisher[Void]

  def remove(username: Username, id: PublicAssetId): Publisher[Void]

  def revoke(username: Username): Publisher[Void]

  def get(username: Username, ids: Set[PublicAssetId]): Publisher[PublicAsset]

  def get(username: Username, id: PublicAssetId): Publisher[PublicAsset] = get(username, Set(id))

  def list(username: Username): Publisher[PublicAsset]

}

class MemoryPublicAssetRepository extends PublicAssetRepository {
  private val tableStore: Table[Username, PublicAssetId, PublicAsset] = Tables.synchronizedTable(HashBasedTable.create())

  override def create(username: Username, creationRequest: PublicAsset): SMono[PublicAsset] =
    SMono.fromCallable(() => {
      tableStore.put(username, creationRequest.id, creationRequest)
      creationRequest
    })

  override def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): SMono[Void] =
    SMono.fromCallable(() => tableStore.get(username, id))
      .switchIfEmpty(SMono.error(PublicAssetNotFoundException(id)))
      .map { publicAsset =>
        val updatedPublicAsset = publicAsset.copy(identityIds = identityIds.toSeq)
        tableStore.put(username, id, updatedPublicAsset)
      }.`then`(SMono.empty)

  override def remove(username: Username, id: PublicAssetId): SMono[Void] =
    SMono.fromCallable(() => tableStore.remove(username, id))
      .`then`(SMono.empty)

  override def revoke(username: Username): SMono[Void] =
    SMono.fromCallable(() => tableStore.row(username).clear())
      .`then`(SMono.empty)

  override def get(username: Username, ids: Set[PublicAssetId]): SFlux[PublicAsset] =
    SFlux.fromIterable(ids.flatMap(id => Option(tableStore.get(username, id))))

  override def list(username: Username): SFlux[PublicAsset] =
    SFlux.fromIterable(tableStore.row(username).values().asScala)
}