package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.encrypted.{KeyId, KeystoreManager}
import com.linagora.tmail.james.jmap.model.KeystoreSetRequest
import javax.inject.Inject
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

object DestroyResults {
  def merge(currentResults: DestroyResults, entry: DestroyResult): DestroyResults =
    entry match {
      case success: DestroySuccess => DestroyResults(currentResults.retrieveDestroyed ++ List(success))
      case failure: DestroyFailure => DestroyResults(currentResults.retrieveNotDestroyed ++ List(failure))
    }

  def empty: DestroyResults = DestroyResults(List())
}

case class DestroyResults(values: List[DestroyResult]) {
  def retrieveDestroyed: List[DestroySuccess] = values.flatMap {
    case value: DestroySuccess => Some(value)
    case _ => None
  }

  def retrieveNotDestroyed: List[DestroyFailure] = values.flatMap {
    case value: DestroyFailure => Some(value)
    case _ => None
  }
}

sealed trait DestroyResult
case class DestroySuccess(id: KeyId) extends DestroyResult
case class DestroyFailure(id: KeyId, throwable: Throwable) extends DestroyResult

class KeystoreSetDestroyPerformer @Inject()(keystore: KeystoreManager) {

  def destroy(mailboxSession: MailboxSession, request: KeystoreSetRequest): SMono[DestroyResults] =
    SFlux.fromIterable(request.destroy.getOrElse(List()))
        .flatMap(id => destroy(mailboxSession, id))
      .fold(DestroyResults.empty)((result: DestroyResults, res: DestroyResult) => DestroyResults.merge(result, res))

  private def destroy(mailboxSession: MailboxSession, id: KeyId): SMono[DestroyResult] =
    SMono.fromPublisher(keystore.delete(mailboxSession.getUser, KeyId(id.value)))
      .`then`(SMono.just(DestroySuccess(id)))
      .onErrorResume(e => SMono.just(DestroyFailure(id, e)))
}
