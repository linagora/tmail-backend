package com.linagora.tmail.james.jmap.publicAsset

import java.io.{ByteArrayInputStream, InputStream}
import com.google.common.collect.{HashBasedTable, Table, Tables}
import com.linagora.tmail.james.jmap.publicAsset.ImageContentType.ImageContentType
import com.linagora.tmail.james.jmap.publicAsset.MemoryPublicAssetRepository.PublicAssetMetadata
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.api.model.Size.Size
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import jakarta.inject.Inject

import scala.jdk.CollectionConverters._

trait PublicAssetRepository {
  def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage]

  def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): Publisher[Void]

  def remove(username: Username, id: PublicAssetId): Publisher[Void]

  def revoke(username: Username): Publisher[Void]

  def get(username: Username, ids: Set[PublicAssetId]): Publisher[PublicAssetStorage]

  def get(username: Username, id: PublicAssetId): Publisher[PublicAssetStorage] = get(username, Set(id))

  def list(username: Username): Publisher[PublicAssetStorage]

  def listAllBlobIds(): Publisher[BlobId]

}

object MemoryPublicAssetRepository {

  object PublicAssetMetadata {
    def from(publicAsset: PublicAssetStorage): PublicAssetMetadata =
      PublicAssetMetadata(
        publicAsset.id,
        publicAsset.publicURI,
        publicAsset.size,
        publicAsset.contentType,
        publicAsset.blobId,
        publicAsset.identityIds)
  }

  case class PublicAssetMetadata(id: PublicAssetId,
                                 publicURI: PublicURI,
                                 size: Size,
                                 contentType: ImageContentType,
                                 blobId: BlobId,
                                 identityIds: Seq[IdentityId]) {

    def asPublicAssetStorage(content: InputStream): PublicAssetStorage =
      PublicAssetStorage(id = id,
        publicURI = publicURI,
        size = size,
        contentType = contentType,
        blobId = blobId,
        identityIds = identityIds,
        content = () => content)
  }
}

class MemoryPublicAssetRepository @Inject()(val blobStore: BlobStore) extends PublicAssetRepository {
  private val tableStore: Table[Username, PublicAssetId, PublicAssetMetadata] = Tables.synchronizedTable(HashBasedTable.create())

  private val bucketName: BucketName = blobStore.getDefaultBucketName

  override def create(username: Username, creationRequest: PublicAssetCreationRequest): SMono[PublicAssetStorage] =
    SMono.fromCallable(() => creationRequest.content.apply().readAllBytes())
      .flatMap((dataAsByte: Array[Byte]) => SMono(blobStore.save(bucketName, dataAsByte, BlobStore.StoragePolicy.LOW_COST))
        .map(blobId => {
          val publicAssetId = PublicAssetIdFactory.generate()
          val publicAsset: PublicAssetStorage = PublicAssetStorage(id = publicAssetId,
            publicURI = creationRequest.publicURI,
            size = creationRequest.size,
            contentType = creationRequest.contentType,
            blobId = blobId,
            identityIds = creationRequest.identityIds,
            content = () => new ByteArrayInputStream(dataAsByte))
          tableStore.put(username, publicAssetId, PublicAssetMetadata.from(publicAsset))
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
}