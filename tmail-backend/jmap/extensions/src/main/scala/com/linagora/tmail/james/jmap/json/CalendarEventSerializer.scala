package com.linagora.tmail.james.jmap.json

import com.google.firestore.v1.Write
import com.linagora.tmail.james.jmap.model.{CalendarDescriptionField, CalendarEventNotFound, CalendarEventNotParsable, CalendarEventParseRequest, CalendarEventParseResponse, CalendarEventParsed, CalendarTitleField}
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json.{Format, JsResult, JsValue, Json, Reads, Writes}

object CalendarEventSerializer {

  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val calendarEventNotFoundWrites: Writes[CalendarEventNotFound] = Json.valueWrites[CalendarEventNotFound]
  private implicit val calendarEventNotParsableWrites: Writes[CalendarEventNotParsable] = Json.valueWrites[CalendarEventNotParsable]
  private implicit val calendarTitleFieldFormat: Writes[CalendarTitleField] = Json.valueWrites[CalendarTitleField]
  private implicit val calendarDescriptionFieldFormat: Writes[CalendarDescriptionField] = Json.valueWrites[CalendarDescriptionField]
  private implicit val calendarEventParsedWrites: Writes[CalendarEventParsed] = Json.writes[CalendarEventParsed]
  private implicit val parsedMapWrites: Writes[Map[BlobId, CalendarEventParsed]] = mapWrites[BlobId, CalendarEventParsed](s => s.value.value, calendarEventParsedWrites)

  private implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private implicit val calendarEventParseResponseWrites: Writes[CalendarEventParseResponse] = Json.writes[CalendarEventParseResponse]
  private implicit val calendarEventParseRequestReads: Reads[CalendarEventParseRequest] = Json.reads[CalendarEventParseRequest]

  def deserializeCalendarEventParseRequest(input: JsValue): JsResult[CalendarEventParseRequest] = Json.fromJson[CalendarEventParseRequest](input)
  def serializeCalendarEventResponse(calendarEventResponse: CalendarEventParseResponse): JsValue = Json.toJson(calendarEventResponse)
}
