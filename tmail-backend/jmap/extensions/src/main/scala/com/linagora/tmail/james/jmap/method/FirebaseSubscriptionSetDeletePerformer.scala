package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.firebase.FirebaseSubscriptionRepository
import com.linagora.tmail.james.jmap.method.FirebaseSubscriptionSetDeletePerformer.{FirebaseSubscriptionDeletionFailure, FirebaseSubscriptionDeletionResult, FirebaseSubscriptionDeletionResults, FirebaseSubscriptionDeletionSuccess}
import com.linagora.tmail.james.jmap.model.{FirebaseSubscriptionId, FirebaseSubscriptionSetRequest, UnparsedFirebaseSubscriptionId}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

object FirebaseSubscriptionSetDeletePerformer {
  sealed trait FirebaseSubscriptionDeletionResult
  case class FirebaseSubscriptionDeletionSuccess(firebaseSubscriptionId: FirebaseSubscriptionId) extends FirebaseSubscriptionDeletionResult
  case class FirebaseSubscriptionDeletionFailure(firebaseSubscriptionId: UnparsedFirebaseSubscriptionId, exception: Throwable) extends FirebaseSubscriptionDeletionResult {
    def asFirebaseSubscriptionSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${firebaseSubscriptionId.id} is not a FirebaseSubscriptionId: ${e.getMessage}"))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class FirebaseSubscriptionDeletionResults(results: Seq[FirebaseSubscriptionDeletionResult]) {
    def destroyed: Seq[FirebaseSubscriptionId] =
      results.flatMap(result => result match {
        case success: FirebaseSubscriptionDeletionSuccess => Some(success)
        case _ => None
      }).map(_.firebaseSubscriptionId)

    def retrieveErrors: Map[UnparsedFirebaseSubscriptionId, SetError] =
      results.flatMap(result => result match {
        case failure: FirebaseSubscriptionDeletionFailure => Some(failure.firebaseSubscriptionId, failure.asFirebaseSubscriptionSetError)
        case _ => None
      })
        .toMap
  }
}

class FirebaseSubscriptionSetDeletePerformer @Inject()(firebaseSubscriptionRepository: FirebaseSubscriptionRepository) {
  def deleteFirebaseSubscriptions(firebaseSubscriptionSetRequest: FirebaseSubscriptionSetRequest, mailboxSession: MailboxSession): SMono[FirebaseSubscriptionDeletionResults] =
    SFlux.fromIterable(firebaseSubscriptionSetRequest.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession)
        .onErrorRecover(e => FirebaseSubscriptionDeletionFailure(unparsedId, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(FirebaseSubscriptionDeletionResults)

  private def delete(unparsedId: UnparsedFirebaseSubscriptionId, mailboxSession: MailboxSession): SMono[FirebaseSubscriptionDeletionResult] =
    unparsedId.parse
      .fold(e => SMono.error(e),
        id => SMono.fromPublisher(firebaseSubscriptionRepository.revoke(mailboxSession.getUser, id))
          .`then`(SMono.just[FirebaseSubscriptionDeletionResult](FirebaseSubscriptionDeletionSuccess(id))))
}
