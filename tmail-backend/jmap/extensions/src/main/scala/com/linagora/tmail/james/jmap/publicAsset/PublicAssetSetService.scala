package com.linagora.tmail.james.jmap.publicAsset

import jakarta.inject.Inject
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
}