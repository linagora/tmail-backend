package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.AttendanceStatus
import com.linagora.tmail.james.jmap.method.{CalendarEventAttendanceGetRequest, CalendarEventAttendanceGetResponse, CalendarEventAttendanceRecord}
import com.linagora.tmail.james.jmap.model.{CalendarEventNotDone, CalendarEventNotFound}
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json.{JsResult, JsValue, Json, OWrites, Reads, Writes}

object CalendarEventAttendanceSerializer {
  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsReads: Reads[BlobIds] = Json.valueReads[BlobIds]
  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]

  private implicit val calendarEventNotFoundWrites: Writes[CalendarEventNotFound] = Json.valueWrites[CalendarEventNotFound]
  private implicit val calendarEventNotDoneWrites: Writes[CalendarEventNotDone] = Json.valueWrites[CalendarEventNotDone]

  private implicit val calendarEventAttendanceGetRequestReads: Reads[CalendarEventAttendanceGetRequest] = Json.reads[CalendarEventAttendanceGetRequest]
  private implicit val attendanceStatusWrites: Writes[AttendanceStatus] = {
    case AttendanceStatus.Accepted => Json.toJson("accepted")
    case AttendanceStatus.Declined => Json.toJson("rejected")
    case AttendanceStatus.NeedsAction => Json.toJson("needsAction")
    case AttendanceStatus.Tentative => Json.toJson("tentativelyAccepted")
  }

  private implicit val calendarEventAttendanceRecordWrites: OWrites[CalendarEventAttendanceRecord] = Json.writes[CalendarEventAttendanceRecord]
  private implicit val calendarEventAttendanceGetResponseWrites: OWrites[CalendarEventAttendanceGetResponse] = Json.writes[CalendarEventAttendanceGetResponse]

  def deserializeEventAttendanceGetRequest(input: JsValue): JsResult[CalendarEventAttendanceGetRequest] =
    Json.fromJson[CalendarEventAttendanceGetRequest](input)

  def serializeEventAttendanceGetResponse(response: CalendarEventAttendanceGetResponse): JsValue =
    Json.toJson(response)

}
