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

package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.james.jmap.model.CalendarEventReplyRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId

import scala.util.{Failure, Success}

object CalendarEventReplyRequest {
  val MAXIMUM_NUMBER_OF_BLOB_IDS: Int = 16

  def extractParsedBlobIds(request: CalendarEventReplyRequest): (CalendarEventNotParsable, Seq[BlobId]) =
    request.blobIds.value.foldLeft((CalendarEventNotParsable(Set.empty), Seq.empty[BlobId])) { (resultBuilder, unparsedBlobId) =>
      BlobId.of(unparsedBlobId) match {
        case Success(blobId) => (resultBuilder._1, resultBuilder._2 :+ blobId)
        case Failure(_) => (resultBuilder._1.merge(CalendarEventNotParsable(Set(unparsedBlobId))), resultBuilder._2)
      }
    }
}

case class CalendarEventReplyRequest(accountId: AccountId,
                                     blobIds: BlobIds,
                                     language: Option[LanguageLocation]) extends WithAccountId {
  def validate(supportedLanguage: Set[String]): Either[Exception, CalendarEventReplyRequest] =
    validateBlobIdsSize
      .flatMap(_.validateLanguage(supportedLanguage))

  private def validateBlobIdsSize: Either[RequestTooLargeException, CalendarEventReplyRequest] =
    if (blobIds.value.length > MAXIMUM_NUMBER_OF_BLOB_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }

  private def validateLanguage(supportedLanguage: Set[String]): Either[IllegalArgumentException, CalendarEventReplyRequest] =
    language match {
      case Some(value) if supportedLanguage.contains(value.value) => scala.Right(this)
      case Some(_) => Left(new IllegalArgumentException(s"The language only supports [${supportedLanguage.mkString(", ")}]"))
      case None => scala.Right(this)
    }
}

trait CalendarEventReplyResponse {
  def accountId: AccountId

  def done: BlobIds

  def notFound: Option[CalendarEventNotFound]

  def notDone: Option[CalendarEventNotDone]
}

object CalendarEventReplyAcceptedResponse {
  def from(accountId: AccountId, results: CalendarEventReplyResults): CalendarEventReplyAcceptedResponse =
    CalendarEventReplyAcceptedResponse(accountId, results.done, results.notFound, results.notDone)
}

case class CalendarEventReplyAcceptedResponse(accountId: AccountId,
                                              accepted: BlobIds,
                                              notFound: Option[CalendarEventNotFound],
                                              notAccepted: Option[CalendarEventNotDone]) extends CalendarEventReplyResponse {
  override val done: BlobIds = accepted
  override val notDone: Option[CalendarEventNotDone] = notAccepted
}

object CalendarEventReplyRejectedResponse {
  def from(accountId: AccountId, results: CalendarEventReplyResults): CalendarEventReplyRejectedResponse =
    CalendarEventReplyRejectedResponse(accountId, results.done, results.notFound, results.notDone)
}

case class CalendarEventReplyRejectedResponse(accountId: AccountId,
                                              reject: BlobIds,
                                              notFound: Option[CalendarEventNotFound],
                                              notRejected: Option[CalendarEventNotDone]) extends CalendarEventReplyResponse {
  override val done: BlobIds = reject
  override val notDone: Option[CalendarEventNotDone] = notRejected
}

object CalendarEventReplyMaybeResponse {
  def from(accountId: AccountId, results: CalendarEventReplyResults): CalendarEventReplyMaybeResponse =
    CalendarEventReplyMaybeResponse(accountId, results.done, results.notFound, results.notDone)
}

case class CalendarEventReplyMaybeResponse(accountId: AccountId,
                                           maybe: BlobIds,
                                           notFound: Option[CalendarEventNotFound],
                                           notMaybe: Option[CalendarEventNotDone]) extends CalendarEventReplyResponse {
  override val done: BlobIds = maybe
  override val notDone: Option[CalendarEventNotDone] = notMaybe
}
