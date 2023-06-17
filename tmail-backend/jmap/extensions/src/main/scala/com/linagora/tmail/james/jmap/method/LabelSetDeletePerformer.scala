package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.label.LabelRepository
import com.linagora.tmail.james.jmap.method.LabelSetDeletePerformer.{LabelDeletionFailure, LabelDeletionResult, LabelDeletionResults, LabelDeletionSuccess}
import com.linagora.tmail.james.jmap.model.{LabelId, LabelSetRequest, UnparsedLabelId}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

object LabelSetDeletePerformer {
  sealed trait LabelDeletionResult
  case class LabelDeletionSuccess(LabelId: LabelId) extends LabelDeletionResult
  case class LabelDeletionFailure(LabelId: UnparsedLabelId, exception: Throwable) extends LabelDeletionResult {
    def asLabelSetError: SetError = exception match {
      case e: IllegalArgumentException => SetError.invalidArguments(SetErrorDescription(s"${LabelId.id} is not a LabelId: ${e.getMessage}"))
      case _ => SetError.serverFail(SetErrorDescription(exception.getMessage))
    }
  }
  case class LabelDeletionResults(results: Seq[LabelDeletionResult]) {
    def destroyed: Seq[LabelId] =
      results.flatMap(result => result match {
        case success: LabelDeletionSuccess => Some(success)
        case _ => None
      }).map(_.LabelId)

    def retrieveErrors: Map[UnparsedLabelId, SetError] =
      results.flatMap(result => result match {
        case failure: LabelDeletionFailure => Some(failure.LabelId, failure.asLabelSetError)
        case _ => None
      })
        .toMap
  }
}

class LabelSetDeletePerformer @Inject()(labelRepository: LabelRepository) {
  def deleteLabels(LabelSetRequest: LabelSetRequest, mailboxSession: MailboxSession): SMono[LabelDeletionResults] =
    SFlux.fromIterable(LabelSetRequest.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession)
        .onErrorRecover(e => LabelDeletionFailure(unparsedId, e)),
        maxConcurrency = 5)
      .collectSeq()
      .map(LabelDeletionResults)

  private def delete(unparsedId: UnparsedLabelId, mailboxSession: MailboxSession): SMono[LabelDeletionResult] =
    SMono.fromPublisher(labelRepository.deleteLabel(mailboxSession.getUser, LabelId(unparsedId.id)))
      .`then`(SMono.just[LabelDeletionResult](LabelDeletionSuccess(LabelId(unparsedId.id))))
}
