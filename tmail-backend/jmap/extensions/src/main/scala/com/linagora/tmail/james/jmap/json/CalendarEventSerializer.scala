package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model._
import org.apache.james.jmap.core.{SetError, UTCDate}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json._

import java.time.ZonedDateTime

object CalendarEventSerializer {

  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val calendarEventNotFoundWrites: Writes[CalendarEventNotFound] = Json.valueWrites[CalendarEventNotFound]
  private implicit val calendarEventNotParsableWrites: Writes[CalendarEventNotParsable] = Json.valueWrites[CalendarEventNotParsable]
  private implicit val calendarTitleFieldFormat: Writes[CalendarTitleField] = Json.valueWrites[CalendarTitleField]
  private implicit val calendarDescriptionFieldFormat: Writes[CalendarDescriptionField] = Json.valueWrites[CalendarDescriptionField]
  private implicit val calendarLocationFieldFormat: Writes[CalendarLocationField] = Json.valueWrites[CalendarLocationField]
  private implicit val timeStampFieldWrites: Writes[ZonedDateTime] = time => JsString(time.format(dateTimeFormatter))
  private implicit val calendarStartFieldWrites: Writes[CalendarStartField] = Json.valueWrites[CalendarStartField]
  private implicit val calendarEndFieldWrites: Writes[CalendarEndField] = Json.valueWrites[CalendarEndField]
  private implicit val utcDateWrites : Writes[UTCDate] = utcDate => JsString(utcDate.asUTC.format(dateTimeUTCFormatter))
  private implicit val calendarDurationWrites : Writes[CalendarDurationField] = duration => JsString(duration.value.toString)
  private implicit val calendarTimeZoneFieldWrites: Writes[CalendarTimeZoneField] = timeZone => JsString(timeZone.value.getID)

  private implicit val calendarEventParsedWrites: Writes[CalendarEventParsed] = Json.writes[CalendarEventParsed]
  private implicit val parsedMapWrites: Writes[Map[BlobId, CalendarEventParsed]] = mapWrites[BlobId, CalendarEventParsed](s => s.value.value, calendarEventParsedWrites)

  private implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private implicit val calendarEventParseResponseWrites: Writes[CalendarEventParseResponse] = Json.writes[CalendarEventParseResponse]
  private implicit val calendarEventParseRequestReads: Reads[CalendarEventParseRequest] = Json.reads[CalendarEventParseRequest]

  def deserializeCalendarEventParseRequest(input: JsValue): JsResult[CalendarEventParseRequest] = Json.fromJson[CalendarEventParseRequest](input)
  def serializeCalendarEventResponse(calendarEventResponse: CalendarEventParseResponse): JsValue = Json.toJson(calendarEventResponse)
}
