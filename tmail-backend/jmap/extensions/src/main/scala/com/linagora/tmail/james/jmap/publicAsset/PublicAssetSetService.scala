package com.linagora.tmail.james.jmap.publicAsset

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.identity.IdentityRepository
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.core.JmapRfc8621Configuration
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetSetService @Inject()(val identityRepository: IdentityRepository,
                                      val publicAssetRepository: PublicAssetRepository,
                                      val configuration: JmapRfc8621Configuration) {
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
        .filter(totalSize => (totalSize + creationRequest.size.value) <= configuration.publicAssetQuotaLimit.asLong())
        .flatMap(_ => SMono(publicAssetRepository.create(username, creationRequest)))
        .switchIfEmpty(SMono.error(new PublicAssetTotalSizeExceededException()))
    }
}

class PublicAssetTotalSizeExceededException() extends RuntimeException {
  override def getMessage(): String = "Exceeding public asset quota limit"
}