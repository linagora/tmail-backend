package com.linagora.tmail.james.jmap.publicAsset

import java.io.ByteArrayInputStream
import java.net.URI

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import jakarta.inject.{Inject, Named}
import org.apache.james.backends.postgres.PostgresModule
import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.blob.api.{BlobId, BlobStore, BucketName}
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.util.ReactorUtils
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class PostgresPublicAssetRepository @Inject()(val executorFactory: PostgresExecutor.Factory,
                                              val blobIdFactory: BlobId.Factory,
                                              val blobStore: BlobStore,
                                              @Named("publicAssetUriPrefix") publicAssetUriPrefix: URI,
                                              @Named(PostgresExecutor.BY_PASS_RLS_INJECT) bypassRlsExecutor: PostgresExecutor) extends PublicAssetRepository {
  private val bucketName: BucketName = blobStore.getDefaultBucketName
  private val byPassRlsDao: PostgresPublicAssetDAO = new PostgresPublicAssetDAO(bypassRlsExecutor, blobIdFactory)

  override def create(username: Username, creationRequest: PublicAssetCreationRequest): Publisher[PublicAssetStorage] =
    SMono.fromCallable(() => creationRequest.content.apply().readAllBytes())
      .flatMap((dataAsByte: Array[Byte]) => SMono(blobStore.save(bucketName, dataAsByte, BlobStore.StoragePolicy.LOW_COST))
        .flatMap(blobId => {
          val assetId: PublicAssetId = PublicAssetIdFactory.generate()
          dao(username).insertAsset(username = username,
            publicAssetMetadata = PublicAssetMetadata(id = assetId,
              publicURI = PublicURI.from(assetId, username, publicAssetUriPrefix),
              size = creationRequest.size,
              contentType = creationRequest.contentType,
              blobId = blobId,
              identityIds = creationRequest.identityIds))
        })
        .map(metadata => metadata.asPublicAssetStorage(new ByteArrayInputStream(dataAsByte))))
      .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)

  override def update(username: Username, id: PublicAssetId, identityIds: Set[IdentityId]): Publisher[Void] = {
    val publicAssetDao: PostgresPublicAssetDAO = dao(username)

    publicAssetDao.selectAsset(username, id)
      .switchIfEmpty(SMono.error(PublicAssetNotFoundException(id)))
      .flatMap(publicAssetMetadata => publicAssetDao.insertAsset(username, publicAssetMetadata.copy(identityIds = identityIds.toSeq)))
      .`then`(SMono.empty)
  }

  override def remove(username: Username, id: PublicAssetId): Publisher[Void] =
    dao(username).deleteAsset(username, id)

  override def revoke(username: Username): Publisher[Void] =
    dao(username).deleteAllAssets(username)

  override def get(username: Username, ids: Set[PublicAssetId]): Publisher[PublicAssetStorage] =
    dao(username).selectAssets(username, ids)
      .flatMap(getBlobContentAndMapToPublicAssetStorage, ReactorUtils.DEFAULT_CONCURRENCY)

  override def get(username: Username, id: PublicAssetId): Publisher[PublicAssetStorage] =
    dao(username).selectAsset(username, id)
      .flatMap(getBlobContentAndMapToPublicAssetStorage)

  override def list(username: Username): Publisher[PublicAssetStorage] =
    dao(username).selectAllAssets(username)
      .flatMap(getBlobContentAndMapToPublicAssetStorage, ReactorUtils.DEFAULT_CONCURRENCY)

  override def listAllBlobIds(): Publisher[BlobId] =
    byPassRlsDao.selectAllBlobIds()

  private def dao(username: Username): PostgresPublicAssetDAO =
    new PostgresPublicAssetDAO(executorFactory.create(username.getDomainPart), blobIdFactory)

  private def getBlobContentAndMapToPublicAssetStorage(metaData: PublicAssetMetadata): SMono[PublicAssetStorage] =
    SMono(blobStore.readReactive(bucketName, metaData.blobId))
      .map(inputStream => metaData.asPublicAssetStorage(inputStream))
}

class PostgresPublicAssetRepositoryModule extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[PostgresPublicAssetRepository]).in(Scopes.SINGLETON)
    bind(classOf[PublicAssetRepository]).to(classOf[PostgresPublicAssetRepository])

    Multibinder.newSetBinder(binder, classOf[PostgresModule])
      .addBinding().toInstance(PublicAssetTable.MODULE)
  }
}
