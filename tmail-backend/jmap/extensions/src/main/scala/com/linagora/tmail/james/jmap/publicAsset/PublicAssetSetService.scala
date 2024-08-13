package com.linagora.tmail.james.jmap.publicAsset

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetSetService @Inject()(val identityRepository: IdentityRepository,
                                      val publicAssetRepository: PublicAssetRepository) {

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
        case _: PublicAssetQuotaLimitExceededException => cleanUpPublicAsset(username)
          .filter(cleanedUpSize => cleanedUpSize >= creationRequest.size.value)
          .switchIfEmpty(SMono.error(new PublicAssetQuotaLimitExceededException))
          .flatMap(_ => SMono(publicAssetRepository.create(username, creationRequest)))
      }

  // return the total size of public assets that was cleaned up
  def cleanUpPublicAsset(username: Username): SMono[Long] =
    SFlux(identityRepository.list(username))
      .map(_.id)
      .collectSeq()
      .flatMapMany(existIdentityIds => listPublicAssetNeedToDelete(username, existIdentityIds))
      .concatMap(publicAssetMetadata => SMono(publicAssetRepository.remove(username, publicAssetMetadata.id))
        .`then`(SMono.just(publicAssetMetadata.size.value)), 1)
      .collectSeq()
      .map(_.sum)

  def listPublicAssetNeedToDelete(username: Username, existIdentityIds: Seq[IdentityId]): SFlux[PublicAssetMetadata] =
    SFlux(publicAssetRepository.listPublicAssetMetaData(username))
      .filter(publicAssetMetadata => publicAssetMetadata.identityIds.isEmpty || !publicAssetMetadata.identityIds.exists(identityId => existIdentityIds.contains(identityId)))

}
