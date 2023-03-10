package com.linagora.tmail.james.jmap.model

import com.google.common.base.Preconditions
import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import eu.timepit.refined.api.Refined
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.component.VEvent
import org.apache.james.jmap.core.{AccountId, Id}
import org.apache.james.jmap.mail.MDNParseRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId

case class CalendarEventParseRequest(accountId: AccountId,
                                     blobIds: BlobIds) extends WithAccountId {
  def validate: Either[RequestTooLargeException, CalendarEventParseRequest] =
    if (blobIds.value.length > MAXIMUM_NUMBER_OF_BLOB_IDS) {
      Left(RequestTooLargeException("The number of ids requested by the client exceeds the maximum number the server is willing to process in a single method call"))
    } else {
      scala.Right(this)
    }
}

object CalendarEventParse {
  type UnparsedBlobId = String Refined Id.IdConstraint
}

case class CalendarEventNotFound(value: Set[UnparsedBlobId]) {
  def merge(other: CalendarEventNotFound): CalendarEventNotFound = CalendarEventNotFound(this.value ++ other.value)
}

case class CalendarEventNotParsable(value: Set[UnparsedBlobId]) {
  def merge(other: CalendarEventNotParsable): CalendarEventNotParsable = CalendarEventNotParsable(this.value ++ other.value)
}

object CalendarEventNotParsable {
  def merge(notParsable1: CalendarEventNotParsable, notParsable2: CalendarEventNotParsable): CalendarEventNotParsable =
    CalendarEventNotParsable(notParsable1.value ++ notParsable2.value)
}

case class CalendarTitleField(value: String) extends AnyVal
case class CalendarDescriptionField(value: String) extends AnyVal

case class InvalidCalendarFileException(blobId: BlobId) extends RuntimeException

object CalendarEventParsed {
  def from(calendar: Calendar): CalendarEventParsed =
    try {
      val vevent: VEvent = calendar.getComponent("VEVENT").asInstanceOf[VEvent]
      Preconditions.checkArgument(vevent != null, "Calendar is not contain VEVENT component".asInstanceOf[Object])
      CalendarEventParsed(title = Option(vevent.getSummary).map(_.getValue).map(CalendarTitleField),
        description = Option(vevent.getDescription).map(_.getValue).map(CalendarDescriptionField))
    }

}
case class CalendarEventParsed(title: Option[CalendarTitleField],
                               description: Option[CalendarDescriptionField])

case class CalendarEventParseResponse(accountId: AccountId,
                            parsed: Option[Map[BlobId, CalendarEventParsed]],
                            notFound: Option[CalendarEventNotFound],
                            notParsable: Option[CalendarEventNotParsable])

object CalendarEventParseResults {
  def notFound(blobId: UnparsedBlobId): CalendarEventParseResults = CalendarEventParseResults(None, Some(CalendarEventNotFound(Set(blobId))), None)

  def notFound(blobId: BlobId): CalendarEventParseResults = CalendarEventParseResults(None, Some(CalendarEventNotFound(Set(blobId.value))), None)

  def notParse(blobId: BlobId): CalendarEventParseResults = CalendarEventParseResults(None, None, Some(CalendarEventNotParsable(Set(blobId.value))))

  def parse(blobId: BlobId, calendarEventParsed: CalendarEventParsed): CalendarEventParseResults = CalendarEventParseResults(Some(Map(blobId -> calendarEventParsed)), None, None)

  def empty(): CalendarEventParseResults = CalendarEventParseResults(None, None, None)

  def merge(response1: CalendarEventParseResults, response2: CalendarEventParseResults): CalendarEventParseResults = CalendarEventParseResults(
    parsed = (response1.parsed ++ response2.parsed).reduceOption((parsed1, parsed2) => parsed1 ++ parsed2),
    notFound = (response1.notFound ++ response2.notFound).reduceOption((notFound1, notFound2) => notFound1.merge(notFound2)),
    notParsable = (response1.notParsable ++ response2.notParsable).reduceOption((notParsable1, notParsable2) => notParsable1.merge(notParsable2)))
}

case class CalendarEventParseResults(parsed: Option[Map[BlobId, CalendarEventParsed]],
                                     notFound: Option[CalendarEventNotFound],
                                     notParsable: Option[CalendarEventNotParsable]) {
  def asResponse(accountId: AccountId): CalendarEventParseResponse = CalendarEventParseResponse(accountId, parsed, notFound, notParsable)
}