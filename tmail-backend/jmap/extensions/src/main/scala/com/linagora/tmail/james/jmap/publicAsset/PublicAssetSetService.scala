package com.linagora.tmail.james.jmap.publicAsset

import jakarta.inject.Inject
import org.apache.james.jmap.api.model.IdentityId
import org.apache.james.jmap.method.IdentityResolver
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

class PublicAssetSetService @Inject()(val identityResolver: IdentityResolver) {
  def checkIdentityIdsExist(identityIds: Seq[IdentityId], session: MailboxSession): SMono[Seq[IdentityId]] =
    SFlux.fromIterable(identityIds)
      .concatMap(identityId => checkIdentityIdExist(identityId, session))
      .collectSeq()

  private def checkIdentityIdExist(identityId: IdentityId, session: MailboxSession): SMono[IdentityId] =
    identityResolver.resolveIdentityId(identityId, session)
      .handle(publishIfPresent)
      .map(_ => identityId)
      .switchIfEmpty(SMono.error(PublicAssetIdentityIdNotFoundException(identityId.serialize)))

  private def publishIfPresent[T]: (Option[T], reactor.core.publisher.SynchronousSink[T]) => Unit =
    (maybeT, sink) => maybeT.foreach(t => sink.next(t))

}