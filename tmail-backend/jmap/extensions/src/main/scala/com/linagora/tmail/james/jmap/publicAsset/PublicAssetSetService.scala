package com.linagora.tmail.james.jmap.publicAsset

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetSetService @Inject()(val identityRepository: IdentityRepository,
                                      val publicAssetRepository: PublicAssetRepository,
                                      val configuration: JMAPExtensionConfiguration) {

  def checkIdentityIdsExist(identityIds: Seq[IdentityId], session: MailboxSession): SMono[Seq[IdentityId]] =
    SFlux(identityRepository.list(session.getUser))
      .map(_.id)
      .collectSeq()
      .map(existIdentityIds => identityIds diff existIdentityIds)
      .flatMap {
        case Seq() => SMono.just(identityIds)
        case noExistIdentityIds => SMono.error(PublicAssetIdentityIdNotFoundException(noExistIdentityIds))
      }

  def create(username: Username, creationRequest: PublicAssetCreationRequest): SMono[PublicAssetStorage] =
    SMono(publicAssetRepository.create(username, creationRequest))
      .onErrorResume {
        case _: PublicAssetQuotaLimitExceededException => cleanUpPublicAsset(username, creationRequest.size.value)
          .filter(cleanedUpSize => cleanedUpSize >= creationRequest.size.value)
          .switchIfEmpty(SMono.error(new PublicAssetQuotaLimitExceededException(configuration.publicAssetTotalSizeLimit.asLong())))
          .flatMap(_ => SMono(publicAssetRepository.create(username, creationRequest)))
      }

  // return the total size of public assets that was cleaned up
  def cleanUpPublicAsset(username: Username, sizeReleaseAsLeastExpected: Long): SMono[Long] =
    SFlux(identityRepository.list(username))
      .map(_.id)
      .collectSeq()
      .flatMap(existIdentityIds => listPublicAssetNeedToDelete(username, existIdentityIds, sizeReleaseAsLeastExpected))
      .flatMapMany(SFlux.fromIterable)
      .flatMapSequential(publicAssetMetadata => SMono(publicAssetRepository.remove(username, publicAssetMetadata.id))
        .`then`(SMono.just(publicAssetMetadata.size.value)), 1)
      .collectSeq().map(_.sum)

  def listPublicAssetNeedToDelete(username: Username, existIdentityIds: Seq[IdentityId], sizeReleaseAsLeast: Long): SMono[Seq[PublicAssetMetadata]] =
    SFlux(publicAssetRepository.listPublicAssetMetaDataOrderByIdAsc(username))
      .filter(publicAssetMetadata => !publicAssetMetadata.identityIds.exists(identityId => existIdentityIds.contains(identityId)))
      .scan((0L, Seq.empty[PublicAssetMetadata])) { case ((totalSize, acc), metadata) =>
        (totalSize + metadata.size.value, acc :+ metadata)
      }
      .takeUntil { case (totalSize, _) => totalSize >= sizeReleaseAsLeast }
      .map { case (_, assetMetadataList) => assetMetadataList }
      .last()
}
