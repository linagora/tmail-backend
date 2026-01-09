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

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.PublicAssetTotalSizeLimit
import jakarta.inject.{Inject, Named}
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class CassandraPublicAssetRepository @Inject()(val dao: CassandraPublicAssetDAO,
                                               val blobStore: BlobStore,
                                               val publicAssetTotalSizeLimit: PublicAssetTotalSizeLimit,
                                               @Named("publicAssetUriPrefix") publicAssetUriPrefix: URI) extends PublicAssetRepository {
  override def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage] =
    SMono(getTotalSize(username))
      .filter(totalSize => (totalSize + creationRequest.size.value) <= publicAssetTotalSizeLimit.asLong())
      .flatMap(_ => SMono(createAsset(username, creationRequest)))
      .switchIfEmpty(SMono.error(PublicAssetQuotaLimitExceededException(publicAssetTotalSizeLimit.asLong())))

  private def createAsset(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage] =
    SMono.fromCallable(() => creationRequest.content.apply().readAllBytes())
      .flatMap((dataAsByte: Array[Byte]) => SMono(blobStore.save(BucketName.DEFAULT, dataAsByte, BlobStore.StoragePolicy.LOW_COST))
        .flatMap(blobId => {
          val assetId: PublicAssetId = PublicAssetIdFactory.generate()
          dao.insertAsset(username = username,
            publicAssetMetadata = PublicAssetMetadata(id = assetId,
              publicURI = PublicURI.from(assetId, username, publicAssetUriPrefix),
              size = creationRequest.size,
              contentType = creationRequest.contentType,
              blobId = blobId,
              identityIds = creationRequest.identityIds))
        })
      .map(metadata => metadata.asPublicAssetStorage(new ByteArrayInputStream(dataAsByte))))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  override def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): Publisher[Void] =
    dao.selectAsset(username, id)
      .switchIfEmpty(SMono.error(PublicAssetNotFoundException(id)))
      .flatMap(publicAssetMetadata => dao.insertAsset(username, publicAssetMetadata.copy(identityIds = identityIds.toSeq)))
      .`then`(SMono.empty)

  override def remove(username: Username, id: PublicAssetId): Publisher[Void] =
    dao.deleteAsset(username, id)

  override def revoke(username: Username): Publisher[Void] =
    dao.deleteAllAssets(username)

  override def get(username: Username, ids: Set[PublicAssetId]): Publisher[PublicAssetStorage] =
    dao.selectAssets(username, ids)
      .flatMap(getBlobContentAndMapToPublicAssetStorage, ReactorUtils.DEFAULT_CONCURRENCY)

  override def get(username: Username, id: PublicAssetId): Publisher[PublicAssetStorage] =
    dao.selectAsset(username, id)
      .flatMap(getBlobContentAndMapToPublicAssetStorage)

  override def list(username: Username): Publisher[PublicAssetStorage] =
    dao.selectAllAssets(username)
      .flatMap(getBlobContentAndMapToPublicAssetStorage, ReactorUtils.DEFAULT_CONCURRENCY)

  override def listAllBlobIds(): Publisher[BlobId] =
    dao.selectAllBlobIds()

  private def getBlobContentAndMapToPublicAssetStorage(metaData: PublicAssetMetadata): SMono[PublicAssetStorage] =
    SMono(blobStore.readReactive(BucketName.DEFAULT, metaData.blobId))
      .map(inputStream => metaData.asPublicAssetStorage(inputStream))

  override def getTotalSize(username: Username): Publisher[Long] =
    dao.selectSize(username).collectSeq().map(sizes => sizes.sum)

  override def listPublicAssetMetaDataOrderByIdAsc(username: Username): Publisher[PublicAssetMetadata] =
    dao.selectAllAssetsOrderByIdAsc(username)
}

case class CassandraPublicAssetRepositoryModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[PublicAssetRepository]).to(classOf[CassandraPublicAssetRepository])

    Multibinder.newSetBinder(binder, classOf[CassandraDataDefinition])
      .addBinding().toInstance(CassandraPublicAssetTable.MODULE)

    bind(classOf[CassandraPublicAssetDAO]).in(Scopes.SINGLETON)
  }
}
