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

import com.linagora.tmail.james.jmap.method.EventCounterAcceptedResults
import com.linagora.tmail.james.jmap.model.CalendarEventReplyRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import eu.timepit.refined.auto._
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId

object CalendarEventCounterAcceptRequest {

  def extractParsedBlobIds(request: CalendarEventCounterAcceptRequest): (CalendarEventNotParsable, Seq[BlobId]) =
    request.blobIds.value.foldLeft((CalendarEventNotParsable(Set.empty), Seq.empty[BlobId])) { (resultBuilder, unparsedBlobId) =>
      BlobId.of(unparsedBlobId) match {
        case scala.util.Success(blobId) => (resultBuilder._1, resultBuilder._2 :+ blobId)
        case scala.util.Failure(_) => (resultBuilder._1.merge(CalendarEventNotParsable(Set(unparsedBlobId))), resultBuilder._2)
      }
    }
}

case class CalendarEventCounterAcceptRequest(accountId: AccountId,
                                             blobIds: BlobIds) extends WithAccountId {

  def validate: Either[Exception, CalendarEventCounterAcceptRequest] = validateBlobIdsSize

  private def validateBlobIdsSize: Either[RequestTooLargeException, CalendarEventCounterAcceptRequest] =
    if (blobIds.value.length > MAXIMUM_NUMBER_OF_BLOB_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call," +
        " the maximum number of ids is " + MAXIMUM_NUMBER_OF_BLOB_IDS))
    } else {
      scala.Right(this)
    }
}

object CalendarEventCounterAcceptedResponse {
  def from(accountId: AccountId, results: EventCounterAcceptedResults): CalendarEventCounterAcceptedResponse =
    CalendarEventCounterAcceptedResponse(accountId, results.done, results.notFound, results.notDone)
}
case class CalendarEventCounterAcceptedResponse(accountId: AccountId,
                                                accepted: BlobIds,
                                                notFound: Option[CalendarEventNotFound],
                                                notAccepted: Option[CalendarEventNotDone])