package com.linagora.tmail.james.jmap.json

import java.time.ZonedDateTime
import com.linagora.tmail.james.jmap.model._
import net.fortuna.ical4j.model.Month
import net.fortuna.ical4j.model.Recur.Skip
import net.fortuna.ical4j.model.WeekDay.Day
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.{SetError, UTCDate}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json._

import java.util.{Date, Locale}

object CalendarEventSerializer {

  private implicit val blobIdReads: Reads[BlobId] = Json.valueReads[BlobId]
  private implicit val blobIdsWrites: Format[BlobIds] = Json.valueFormat[BlobIds]
  private implicit val calendarEventNotFoundWrites: Writes[CalendarEventNotFound] = Json.valueWrites[CalendarEventNotFound]
  private implicit val calendarEventNotParsableWrites: Writes[CalendarEventNotParsable] = Json.valueWrites[CalendarEventNotParsable]
  private implicit val calendarTitleFieldFormat: Writes[CalendarTitleField] = Json.valueWrites[CalendarTitleField]
  private implicit val calendarAttendeeNameWrites: Writes[CalendarAttendeeName] = Json.valueWrites[CalendarAttendeeName]
  private implicit val calendarAttendeeKindWrites: Writes[CalendarAttendeeKind] = Json.valueWrites[CalendarAttendeeKind]
  private implicit val calendarAttendeeRoleWrites: Writes[CalendarAttendeeRole] = Json.valueWrites[CalendarAttendeeRole]
  private implicit val calendarAttendeeMailToWrites: Writes[CalendarAttendeeMailTo] = mail => JsString(mail.serialize())
  private implicit val calendarAttendeeParticipationStatusWrites: Writes[CalendarAttendeeParticipationStatus] = Json.valueWrites[CalendarAttendeeParticipationStatus]
  private implicit val calendarAttendeeExpectReplyWrites: Writes[CalendarAttendeeExpectReply] = Json.valueWrites[CalendarAttendeeExpectReply]
  private implicit val calendarAttendeeFieldWrites: Writes[CalendarAttendeeField] = Json.writes[CalendarAttendeeField]
  private implicit val calendarDescriptionFieldFormat: Writes[CalendarDescriptionField] = Json.valueWrites[CalendarDescriptionField]
  private implicit val calendarLocationFieldFormat: Writes[CalendarLocationField] = Json.valueWrites[CalendarLocationField]
  private implicit val mailAddressWrites: Writes[MailAddress] = mail => JsString(mail.toString)
  private implicit val calendarOrganizerFieldWrites: Writes[CalendarOrganizerField] = Json.writes[CalendarOrganizerField]
  private implicit val timeStampFieldWrites: Writes[ZonedDateTime] = time => JsString(time.format(dateTimeFormatter))
  private implicit val calendarStartFieldWrites: Writes[CalendarStartField] = Json.valueWrites[CalendarStartField]
  private implicit val calendarEndFieldWrites: Writes[CalendarEndField] = Json.valueWrites[CalendarEndField]
  private implicit val utcDateWrites : Writes[UTCDate] = utcDate => JsString(utcDate.asUTC.format(dateTimeUTCFormatter))
  private implicit val calendarDurationWrites : Writes[CalendarDurationField] = duration => JsString(duration.value.toString)
  private implicit val calendarTimeZoneFieldWrites: Writes[CalendarTimeZoneField] = timeZone => JsString(timeZone.value.getID)

  private implicit val calendarParticipantsFieldWrites: Writes[CalendarParticipantsField] = Json.valueWrites[CalendarParticipantsField]
  private implicit val calendarExtensionFieldsWrites: Writes[CalendarExtensionFields] = Json.valueWrites[CalendarExtensionFields]
  private implicit val calendarMethodFieldWrites: Writes[CalendarMethodField] = Json.valueWrites[CalendarMethodField]
  private implicit val calendarSequenceFieldWrites: Writes[CalendarSequenceField] = Json.valueWrites[CalendarSequenceField]
  private implicit val calendarUidFieldWrites: Writes[CalendarUidField] = Json.valueWrites[CalendarUidField]
  private implicit val calendarPriorityFieldWrites: Writes[CalendarPriorityField] = Json.valueWrites[CalendarPriorityField]
  private implicit val calendarFreeBusyStatusFieldWrites: Writes[CalendarFreeBusyStatusField] = Json.valueWrites[CalendarFreeBusyStatusField]
  private implicit val calendarPrivacyFieldWrites: Writes[CalendarPrivacyField] = Json.valueWrites[CalendarPrivacyField]
  private implicit val recurrenceRulesRScaleWrites: Writes[RecurrenceRulesRScale] = Json.valueWrites[RecurrenceRulesRScale]
  private implicit val calendarEventMonthWrites: Writes[Month] = month => JsString(month.toString)
  private implicit val calendarEventDayWrites: Writes[Day] = day => JsString(day.toString.toLowerCase(Locale.US))
  private implicit val calendarEventSkipWrites: Writes[Skip] = skip => JsString(skip.toString.toLowerCase(Locale.US))
  private implicit val recurrenceRulesIntervalWrites: Writes[RecurrenceRulesInterval] = interval => JsNumber(interval.value.value)
  private implicit val recurrenceRulesCountWrites: Writes[RecurrenceRulesCount] = count => JsNumber(count.value.value)
  private implicit val recurrenceRulesFrequencyWrites: Writes[RecurrenceRulesFrequency] = frequency => JsString(frequency.value.name().toLowerCase(Locale.US))
  private implicit val calendarEventByDayWrites: Writes[CalendarEventByDay] = Json.valueWrites[CalendarEventByDay]
  private implicit val calendarEventByMonthWrites: Writes[CalendarEventByMonth] = Json.valueWrites[CalendarEventByMonth]
  private implicit val recurrenceRulesUtilWrites: Writes[RecurrenceRulesUntil] = utcDate => JsString(utcDate.value.asUTC.format(dateTimeUTCFormatter))
  private implicit val calendarRecurrenceRulesWrites: Writes[RecurrenceRules] = Json.writes[RecurrenceRules]
  private implicit val calendarRecurrenceRulesFieldWrites: Writes[RecurrenceRulesField] = Json.valueWrites[RecurrenceRulesField]

  private implicit val calendarEventParsedWrites: Writes[CalendarEventParsed] = Json.writes[CalendarEventParsed]
  private implicit val parsedMapWrites: Writes[Map[BlobId, CalendarEventParsed]] = mapWrites[BlobId, CalendarEventParsed](s => s.value.value, calendarEventParsedWrites)

  private implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private implicit val calendarEventParseResponseWrites: Writes[CalendarEventParseResponse] = Json.writes[CalendarEventParseResponse]
  private implicit val calendarEventParseRequestReads: Reads[CalendarEventParseRequest] = Json.reads[CalendarEventParseRequest]

  def deserializeCalendarEventParseRequest(input: JsValue): JsResult[CalendarEventParseRequest] = Json.fromJson[CalendarEventParseRequest](input)
  def serializeCalendarEventResponse(calendarEventResponse: CalendarEventParseResponse): JsValue = Json.toJson(calendarEventResponse)
}
