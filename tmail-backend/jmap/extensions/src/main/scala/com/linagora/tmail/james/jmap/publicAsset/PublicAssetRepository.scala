package com.linagora.tmail.james.jmap.publicAsset

import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Clock

import com.google.common.collect.{HashBasedTable, ImmutableList, Table, Tables}
import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import jakarta.inject.{Inject, Named}
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

trait PublicAssetRepository {
  def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage]

  def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): Publisher[Void]

  def remove(username: Username, id: PublicAssetId): Publisher[Void]

  def revoke(username: Username): Publisher[Void]

  def get(username: Username, ids: Set[PublicAssetId]): Publisher[PublicAssetStorage]

  def get(username: Username, id: PublicAssetId): Publisher[PublicAssetStorage] = get(username, Set(id))

  def list(username: Username): Publisher[PublicAssetStorage]

  def listPublicAssetMetaData(username: Username): Publisher[PublicAssetMetadata]

  def listAllBlobIds(): Publisher[BlobId]

  def updateIdentityIds(username: Username, id: PublicAssetId, identityIdsToAdd: Seq[IdentityId], identityIdsToRemove: Seq[IdentityId]): Publisher[Void] =
    SMono(get(username, id))
      .map(publicAsset => (publicAsset.identityIds.toSet ++ identityIdsToAdd.toSet) -- identityIdsToRemove.toSet)
      .flatMap(identityIds => SMono(update(username, id, identityIds)))

  def getTotalSize(username: Username): Publisher[Long]
}

class MemoryPublicAssetRepository @Inject()(val blobStore: BlobStore,
                                            val configuration: JMAPExtensionConfiguration,
                                            @Named("publicAssetUriPrefix") publicAssetUriPrefix: URI,
                                            clock: Clock) extends PublicAssetRepository {
  private val tableStore: Table[Username, PublicAssetId, PublicAssetMetadata] = Tables.synchronizedTable(HashBasedTable.create())

  private val bucketName: BucketName = blobStore.getDefaultBucketName

  override def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage] =
    SMono(getTotalSize(username))
      .filter(totalSize => (totalSize + creationRequest.size.value) <= configuration.publicAssetTotalSizeLimit.asLong())
      .flatMap(_ => SMono(createAsset(username, creationRequest)))
      .switchIfEmpty(SMono.error(PublicAssetQuotaLimitExceededException()))

  private def createAsset(username: Username, creationRequest: PublicAssetCreationRequest): SMono[PublicAssetStorage] =
    SMono.fromCallable(() => creationRequest.content.apply().readAllBytes())
      .flatMap((dataAsByte: Array[Byte]) => SMono(blobStore.save(bucketName, dataAsByte, BlobStore.StoragePolicy.LOW_COST))
        .map(blobId => {
          val publicAssetId = PublicAssetIdFactory.generate()
          val publicAsset: PublicAssetStorage = PublicAssetStorage(id = publicAssetId,
            publicURI = PublicURI.from(publicAssetId, username, publicAssetUriPrefix),
            size = creationRequest.size,
            contentType = creationRequest.contentType,
            blobId = blobId,
            identityIds = creationRequest.identityIds,
            content = () => new ByteArrayInputStream(dataAsByte))
          tableStore.put(username, publicAssetId, PublicAssetMetadata.from(publicAsset)
          .copy(createdDate = clock.instant()))
          publicAsset
        })).subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

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

  override def get(username: Username, ids: Set[PublicAssetId]): SFlux[PublicAssetStorage] =
    SFlux.fromIterable(ids.flatMap(id => Option(tableStore.get(username, id))))
      .flatMap(getBlobContentAndMapToPublicAssetStorage, ReactorUtils.DEFAULT_CONCURRENCY)

  override def list(username: Username): SFlux[PublicAssetStorage] =
    SFlux.fromIterable(tableStore.row(username).values().asScala)
      .flatMap(getBlobContentAndMapToPublicAssetStorage, ReactorUtils.DEFAULT_CONCURRENCY)

  private def getBlobContentAndMapToPublicAssetStorage(metaData: PublicAssetMetadata): SMono[PublicAssetStorage] =
    SMono(blobStore.readReactive(bucketName, metaData.blobId))
      .map(bytes => metaData.asPublicAssetStorage(bytes))

  override def listAllBlobIds(): Publisher[BlobId] =
    SFlux.fromIterable(tableStore.values().asScala.map(_.blobId))

  override def getTotalSize(username: Username): Publisher[Long] =
    SMono.just(tableStore.row(username)
      .values()
      .asScala
      .map(publicAssetMetadata => publicAssetMetadata.size.value)
      .sum)

  override def listPublicAssetMetaData(username: Username): Publisher[PublicAssetMetadata] =
    SFlux.fromIterable(ImmutableList.copyOf(tableStore.row(username).values()).asScala)
}