/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.method

import com.google.inject.Inject
import com.linagora.tmail.james.jmap.label.LabelRepository
import com.linagora.tmail.james.jmap.method.LabelSetDeletePerformer.{LabelDeletionFailure, LabelDeletionResult, LabelDeletionResults, LabelDeletionSuccess}
import com.linagora.tmail.james.jmap.model.{LabelId, LabelSetRequest, UnparsedLabelId}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.mailbox.MailboxSession
import org.apache.james.util.ReactorUtils
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

class LabelSetDeletePerformer @Inject()(val labelRepository: LabelRepository) {
  def deleteLabels(LabelSetRequest: LabelSetRequest, mailboxSession: MailboxSession): SMono[LabelDeletionResults] =
    SFlux.fromIterable(LabelSetRequest.destroy.getOrElse(Seq()))
      .flatMap(unparsedId => delete(unparsedId, mailboxSession)
        .onErrorRecover(e => LabelDeletionFailure(unparsedId, e)),
        maxConcurrency = ReactorUtils.DEFAULT_CONCURRENCY)
      .collectSeq()
      .map(LabelDeletionResults)

  private def delete(unparsedId: UnparsedLabelId, mailboxSession: MailboxSession): SMono[LabelDeletionResult] =
    SMono.fromPublisher(labelRepository.deleteLabel(mailboxSession.getUser, unparsedId.asLabelId))
        .`then`(SMono.just[LabelDeletionResult](LabelDeletionSuccess(unparsedId.asLabelId)))
}
