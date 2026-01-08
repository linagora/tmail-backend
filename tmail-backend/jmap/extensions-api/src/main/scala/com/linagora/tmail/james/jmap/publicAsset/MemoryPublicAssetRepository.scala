/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.publicAsset

import java.io.ByteArrayInputStream
import java.net.URI
import java.time.Clock

import com.google.common.collect.{HashBasedTable, ImmutableList, Table, Tables}
import com.linagora.tmail.james.jmap.PublicAssetTotalSizeLimit
import jakarta.inject.{Inject, Named}
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class MemoryPublicAssetRepository @Inject()(val blobStore: BlobStore,
                                            val publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit,
                                            @Named("publicAssetUriPrefix") publicAssetUriPrefix: URI) extends PublicAssetRepository {
  private val tableStore: Table[Username, PublicAssetId, PublicAssetMetadata] = Tables.synchronizedTable(HashBasedTable.create())

  override def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage] =
    SMono(getTotalSize(username))
      .filter(totalSize => (totalSize + creationRequest.size.value) <= publicAssetTotalSizeLimit.asLong())
      .flatMap(_ => SMono(createAsset(username, creationRequest)))
      .switchIfEmpty(SMono.error(PublicAssetQuotaLimitExceededException(publicAssetTotalSizeLimit.asLong())))

  private def createAsset(username: Username, creationRequest: PublicAssetCreationRequest): SMono[PublicAssetStorage] =
    SMono.fromCallable(() => creationRequest.content.apply().readAllBytes())
      .flatMap((dataAsByte: Array[Byte]) => SMono(blobStore.save(BucketName.DEFAULT, dataAsByte, BlobStore.StoragePolicy.LOW_COST))
        .map(blobId => {
          val publicAssetId = PublicAssetIdFactory.generate()
          val publicAsset: PublicAssetStorage = PublicAssetStorage(id = publicAssetId,
            publicURI = PublicURI.from(publicAssetId, username, publicAssetUriPrefix),
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
    SMono(blobStore.readReactive(BucketName.DEFAULT, metaData.blobId))
      .map(bytes => metaData.asPublicAssetStorage(bytes))

  override def listAllBlobIds(): Publisher[BlobId] =
    SFlux.fromIterable(tableStore.values().asScala.map(_.blobId))

  override def getTotalSize(username: Username): Publisher[Long] =
    SMono.just(tableStore.row(username)
      .values()
      .asScala
      .map(publicAssetMetadata => publicAssetMetadata.size.value)
      .sum)

  override def listPublicAssetMetaDataOrderByIdAsc(username: Username): Publisher[PublicAssetMetadata] =
    SFlux.fromIterable(ImmutableList.copyOf(tableStore.row(username).values()).asScala)
      .sort((a, b) => a.id.value.compareTo(b.id.value))
}