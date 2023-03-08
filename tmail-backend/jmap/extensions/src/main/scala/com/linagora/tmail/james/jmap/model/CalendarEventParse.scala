package com.linagora.tmail.james.jmap.model

import com.google.common.base.Preconditions
import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import com.linagora.tmail.james.jmap.model.CalendarStartField.getTimeZoneAlternative
import eu.timepit.refined.api.Refined
import net.fortuna.ical4j.data.{CalendarBuilder, CalendarParserFactory, ContentHandlerContext}
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.{Calendar, Property, TimeZoneRegistryFactory}
import org.apache.james.jmap.core.{AccountId, Id, UTCDate}
import org.apache.james.jmap.mail.MDNParseRequest.MAXIMUM_NUMBER_OF_BLOB_IDS
import org.apache.james.jmap.mail.{BlobId, BlobIds, RequestTooLargeException}
import org.apache.james.jmap.method.WithAccountId

import java.io.InputStream
import java.time.{Duration, ZoneId, ZonedDateTime}
import java.util.TimeZone

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

object CalendarTitleField {
  def from(calendarEvent: VEvent): Option[CalendarTitleField] =
    Option(calendarEvent.getSummary).map(_.getValue).map(CalendarTitleField(_))
}

case class CalendarTitleField(value: String) extends AnyVal

object CalendarDescriptionField {
  def from(calendarEvent: VEvent): Option[CalendarDescriptionField] =
    Option(calendarEvent.getDescription).map(_.getValue).map(CalendarDescriptionField(_))
}

case class CalendarDescriptionField(value: String) extends AnyVal

object CalendarLocationField {
  def from(calendarEvent: VEvent): Option[CalendarLocationField] =
    Option(calendarEvent.getLocation).map(_.getValue).map(CalendarLocationField(_))
}

case class CalendarLocationField(value: String) extends AnyVal

object CalendarStartField {
  def from(calendarEvent: VEvent): Option[CalendarStartField] =
    Option(calendarEvent.getStartDate)
      .flatMap(startDate => Option(startDate.getDate)
        .map(date => CalendarStartField(ZonedDateTime.ofInstant(date.toInstant,
          Option(startDate.getTimeZone).getOrElse(getTimeZoneAlternative(calendarEvent)).toZoneId))))

  def getTimeZoneAlternative(calendarEvent: VEvent): TimeZone =
    CalendarTimeZoneField.getTimeZoneFromTZID(calendarEvent)
      .getOrElse(TimeZone.getTimeZone(ZoneId.of("UTC")))
}

case class CalendarStartField(value: ZonedDateTime) extends AnyVal {
  def asUtcDate(): UTCDate = UTCDate(value)
}

object CalendarEndField {
  def from(calendarEvent: VEvent): Option[CalendarEndField] =
    Option(calendarEvent.getEndDate())
      .flatMap(endDate => Option(endDate.getDate)
        .map(date => CalendarEndField(ZonedDateTime.ofInstant(date.toInstant,
          Option(endDate.getTimeZone).getOrElse(getTimeZoneAlternative(calendarEvent)).toZoneId))))
}

case class CalendarEndField(value: ZonedDateTime) extends AnyVal {
  def asUtcDate(): UTCDate = UTCDate(value)
}

object CalendarDurationField {
  def from(calendarEvent: VEvent): Option[CalendarDurationField] =
    Option(calendarEvent.getDuration)
      .map(_.getDuration)
      .map(java.time.Duration.from)
      .map(CalendarDurationField(_))
}

case class CalendarDurationField(value: Duration) extends AnyVal

object CalendarTimeZoneField {
  def from(calendarEvent: VEvent): Option[CalendarTimeZoneField] =
    getTimeZoneFromTZID(calendarEvent)
      .orElse(getTimeZoneFromStartDate(calendarEvent))
      .map(CalendarTimeZoneField(_))

  def getTimeZoneFromTZID(calendarEvent: VEvent): Option[TimeZone] =
    Option(calendarEvent.getProperty(Property.TZID).asInstanceOf[Property])
      .map(_.getValue)
      .map(TimeZone.getTimeZone)

  private def getTimeZoneFromStartDate(calendarEvent: VEvent): Option[TimeZone] =
    Option(calendarEvent.getStartDate)
      .flatMap(date => Option(date.getTimeZone))
      .map(_.toZoneId)
      .map(TimeZone.getTimeZone)
}

case class CalendarTimeZoneField(value: TimeZone) extends AnyVal

case class InvalidCalendarFileException(blobId: BlobId) extends RuntimeException

object CalendarEventParsed {
  def from(calendarContent: InputStream): CalendarEventParsed =
    from(new CalendarBuilder(CalendarParserFactory.getInstance.get,
      new ContentHandlerContext().withSupressInvalidProperties(true),
      TimeZoneRegistryFactory.getInstance.createRegistry)
      .build(calendarContent))

  def from(calendar: Calendar): CalendarEventParsed =
    try {
      val vevent: VEvent = calendar.getComponent("VEVENT").asInstanceOf[VEvent]
      Preconditions.checkArgument(vevent != null, "Calendar is not contain VEVENT component".asInstanceOf[Object])
      val start: Option[CalendarStartField] = CalendarStartField.from(vevent)
      val end: Option[CalendarEndField] = CalendarEndField.from(vevent)

      CalendarEventParsed(title = CalendarTitleField.from(vevent),
        description = CalendarDescriptionField.from(vevent),
        start = start,
        end = end,
        utcStart = start.map(_.asUtcDate()),
        utcEnd = end.map(_.asUtcDate()),
        duration = CalendarDurationField.from(vevent),
        timeZone = CalendarTimeZoneField.from(vevent),
        location = CalendarLocationField.from(vevent))
    }
}

case class CalendarEventParsed(title: Option[CalendarTitleField] = None,
                               description: Option[CalendarDescriptionField] = None,
                               start: Option[CalendarStartField] = None,
                               end: Option[CalendarEndField] = None,
                               utcStart: Option[UTCDate] = None,
                               utcEnd: Option[UTCDate] = None,
                               timeZone: Option[CalendarTimeZoneField] = None,
                               duration: Option[CalendarDurationField] = None,
                               location: Option[CalendarLocationField] = None)

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