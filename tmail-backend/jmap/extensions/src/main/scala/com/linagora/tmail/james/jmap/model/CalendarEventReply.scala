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

import java.util.Locale

import com.linagora.tmail.james.jmap.method.CalendarEventReplyPerformer.LOGGER
import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import com.linagora.tmail.james.jmap.model.CalendarEventReplyRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, SetError}
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId
import org.apache.james.mailbox.MailboxSession

import scala.util.Try

object LanguageLocation {
  def fromString(languageCode: String): Try[LanguageLocation] = detectLocale(languageCode).map(LanguageLocation.apply)

  def detectLocale(languageCode: String): Try[Locale] =
    if (Locale.getISOLanguages.contains(languageCode)) {
      Try(Locale.forLanguageTag(languageCode))
    } else {
      throw new IllegalArgumentException("The language must be a valid ISO language code")
    }
}

case class LanguageLocation(language: Locale) {
  def value: String = language.toLanguageTag
}

object CalendarEventReplyRequest {
  val MAXIMUM_NUMBER_OF_BLOB_IDS: Int = 16
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

object CalendarEventReplyResults {
  def merge(r1: CalendarEventReplyResults, r2: CalendarEventReplyResults): CalendarEventReplyResults =
    CalendarEventReplyResults(
      done = BlobIds(r1.done.value ++ r2.done.value),
      notFound = (r1.notFound ++ r2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
      notDone = (r1.notDone ++ r2.notDone).reduceOption((notDone1, notDOne2) => notDone1.merge(notDOne2)))

  def notFound(blobId: BlobId): CalendarEventReplyResults = CalendarEventReplyResults(notFound = Some(CalendarEventNotFound(Set(blobId.value))))

  def empty: CalendarEventReplyResults = CalendarEventReplyResults()

  def notDone(notParsable: CalendarEventNotParsable): CalendarEventReplyResults =
    CalendarEventReplyResults(notDone = Some(CalendarEventNotDone(notParsable.asSetErrorMap)))

  def notDone(blobId: BlobId, throwable: Throwable, mailboxSession: MailboxSession): CalendarEventReplyResults =
    CalendarEventReplyResults(notDone = Some(CalendarEventNotDone(Map(blobId.value -> asSetError(throwable, mailboxSession)))))

  private def asSetError(throwable: Throwable, mailboxSession: MailboxSession): SetError = throwable match {
    case _: InvalidCalendarFileException | _: IllegalArgumentException =>
      LOGGER.info("Error when generate reply mail for {}: {}", mailboxSession.getUser.asString(), throwable.getMessage)
      SetError.invalidPatch(SetErrorDescription(throwable.getMessage))
    case _ =>
      LOGGER.error("serverFail to generate reply mail for {}", mailboxSession.getUser.asString(), throwable)
      SetError.serverFail(SetErrorDescription(throwable.getMessage))
  }
}

case class CalendarEventNotDone(value: Map[UnparsedBlobId, SetError]) {
  def merge(other: CalendarEventNotDone): CalendarEventNotDone = CalendarEventNotDone(this.value ++ other.value)
}

case class CalendarEventReplyResults(done: BlobIds = BlobIds(Seq.empty),
                                     notFound: Option[CalendarEventNotFound] = None,
                                     notDone: Option[CalendarEventNotDone] = None)

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
