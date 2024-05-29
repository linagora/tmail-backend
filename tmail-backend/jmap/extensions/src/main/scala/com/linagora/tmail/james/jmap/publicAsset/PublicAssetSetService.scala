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

  def create(username: Username, creationRequest: PublicAssetCreationRequest): SMono[PublicAssetStorage] = {
    SMono(publicAssetRepository.getTotalSize(username))
      .filter(totalSize => (totalSize + creationRequest.size.value) <= configuration.publicAssetTotalSizeLimit.asLong())
      .flatMap(_ => SMono(publicAssetRepository.create(username, creationRequest)))
      .switchIfEmpty(SMono.error(new PublicAssetQuotaLimitExceededException()))
  }
}