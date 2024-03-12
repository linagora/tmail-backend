package com.linagora.tmail.james.jmap.model

import java.util.Locale

import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import com.linagora.tmail.james.jmap.model.CalendarEventReplyRequest.{LANGUAGE_SUPPORTED, MAXIMUM_NUMBER_OF_BLOB_IDS}
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId

import scala.util.Try

object LanguageLocation {
  def fromString(languageCode: String): Try[LanguageLocation] = {
    if (Locale.getISOLanguages.contains(languageCode)) {
      Try(Locale.forLanguageTag(languageCode)).map(LanguageLocation.apply)
    } else {
      throw new IllegalArgumentException("The language must be a valid ISO language code")
    }
  }
}

case class LanguageLocation(language: Locale) {
  def validate: Either[IllegalArgumentException, LanguageLocation] =
    if (language.getLanguage.isEmpty) {
      Left(new IllegalArgumentException("The language must not be empty"))
    } else {
      scala.Right(this)
    }

  def value: String = language.toLanguageTag
}

object CalendarEventReplyRequest {
  val MAXIMUM_NUMBER_OF_BLOB_IDS: Int = 16
  val LANGUAGE_SUPPORTED: Set[String] = Set("fr", "en")
}

case class CalendarEventReplyRequest(accountId: AccountId,
                                     blobIds: BlobIds,
                                     language: Option[LanguageLocation]) extends WithAccountId {
  def validate: Either[Exception, CalendarEventReplyRequest] =
    validateBlobIdsSize
      .flatMap(_.validateLanguage)

  private def validateBlobIdsSize: Either[RequestTooLargeException, CalendarEventReplyRequest] =
    if (blobIds.value.length > MAXIMUM_NUMBER_OF_BLOB_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }

  private def validateLanguage: Either[IllegalArgumentException, CalendarEventReplyRequest] =
    language match {
      case Some(value) if LANGUAGE_SUPPORTED.contains(value.value) => scala.Right(this)
      case Some(_) => Left(new IllegalArgumentException("The language only supports [fr, en]"))
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
    CalendarEventReplyResults(notDone = Some(CalendarEventNotDone(notParsable.value)))

  def notDone(blobId: BlobId): CalendarEventReplyResults = CalendarEventReplyResults(notDone = Some(CalendarEventNotDone(Set(blobId.value))))

}


case class CalendarEventNotDone(value: Set[UnparsedBlobId]) {
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

case class CalendarEventReplyRejectResponse(accountId: AccountId,
                                            reject: BlobIds,
                                            notFound: Option[CalendarEventNotFound],
                                            notRejected: Option[CalendarEventNotDone]) extends CalendarEventReplyResponse {
  override val done: BlobIds = reject
  override val notDone: Option[CalendarEventNotDone] = notRejected
}



