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

package com.linagora.tmail.james.jmap.json

import java.util.Locale

import com.linagora.tmail.james.jmap.model._
import net.fortuna.ical4j.model.Month
import net.fortuna.ical4j.model.Recur.Skip
import net.fortuna.ical4j.model.WeekDay.Day
import org.apache.james.jmap.core.{Properties, SetError}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, BlobIds}
import play.api.libs.json._

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
  private implicit val calendarOrganizerFieldWrites: Writes[CalendarOrganizerField] = Json.writes[CalendarOrganizerField]
  private implicit val calendarStartFieldWrites: Writes[CalendarStartField] = Json.valueWrites[CalendarStartField]
  private implicit val calendarEndFieldWrites: Writes[CalendarEndField] = Json.valueWrites[CalendarEndField]
  private implicit val calendarDurationWrites : Writes[CalendarDurationField] = duration => JsString(duration.value.toString)
  private implicit val calendarTimeZoneFieldWrites: Writes[CalendarTimeZoneField] = timeZone => JsString(timeZone.value.getID)

  private implicit val calendarParticipantsFieldWrites: Writes[CalendarParticipantsField] = Json.valueWrites[CalendarParticipantsField]
  private implicit val calendarExtensionFieldsWrites: Writes[CalendarExtensionFields] = Json.valueWrites[CalendarExtensionFields]
  private implicit val calendarMethodFieldWrites: Writes[CalendarMethodField] = Json.valueWrites[CalendarMethodField]
  private implicit val calendarSequenceFieldWrites: Writes[CalendarSequenceField] = Json.valueWrites[CalendarSequenceField]
  private implicit val calendarUidFieldWrites: Writes[CalendarUidField] = Json.valueWrites[CalendarUidField]
  private implicit val calendarPriorityFieldWrites: Writes[CalendarPriorityField] = Json.valueWrites[CalendarPriorityField]
  private implicit val calendarFreeBusyStatusFieldWrites: Writes[CalendarFreeBusyStatusField] = Json.valueWrites[CalendarFreeBusyStatusField]
  private implicit val calendarEventStatusFieldWrites: Writes[CalendarEventStatusField] = Json.valueWrites[CalendarEventStatusField]
  private implicit val calendarPrivacyFieldWrites: Writes[CalendarPrivacyField] = Json.valueWrites[CalendarPrivacyField]
  private implicit val recurrenceRuleRScaleWrites: Writes[RecurrenceRuleRScale] = Json.valueWrites[RecurrenceRuleRScale]
  private implicit val calendarEventMonthWrites: Writes[Month] = month => JsString(month.toString)
  private implicit val calendarEventDayWrites: Writes[Day] = day => JsString(day.toString.toLowerCase(Locale.US))
  private implicit val calendarEventSkipWrites: Writes[Skip] = skip => JsString(skip.toString.toLowerCase(Locale.US))
  private implicit val recurrenceRuleIntervalWrites: Writes[RecurrenceRuleInterval] = interval => JsNumber(interval.value.value)
  private implicit val recurrenceRuleCountWrites: Writes[RecurrenceRuleCount] = count => JsNumber(count.value.value)
  private implicit val recurrenceRuleFrequencyWrites: Writes[RecurrenceRuleFrequency] = frequency => JsString(frequency.value.name().toLowerCase(Locale.US))
  private implicit val calendarEventByDayWrites: Writes[CalendarEventByDay] = Json.valueWrites[CalendarEventByDay]
  private implicit val calendarEventByMonthWrites: Writes[CalendarEventByMonth] = Json.valueWrites[CalendarEventByMonth]
  private implicit val recurrenceRuleUtilWrites: Writes[RecurrenceRuleUntil] = Json.valueWrites[RecurrenceRuleUntil]
  private implicit val calendarRecurrenceRuleWrites: Writes[RecurrenceRule] = Json.writes[RecurrenceRule]
  private implicit val calendarRecurrenceRulesFieldWrites: Writes[RecurrenceRulesField] = Json.valueWrites[RecurrenceRulesField]
  private implicit val calendarExcludedRecurrenceRulesFieldWrites: Writes[ExcludedRecurrenceRulesField] = Json.valueWrites[ExcludedRecurrenceRulesField]
  private implicit val calendarRecurrenceIdFieldWrites: Writes[RecurrenceIdField] = Json.valueWrites[RecurrenceIdField]

  private implicit val calendarEventParsedWrites: Writes[CalendarEventParsed] = Json.writes[CalendarEventParsed]
  private implicit val calendarEventParsedListWrites: Writes[CalendarEventParsedList] = Json.valueWrites[CalendarEventParsedList]
  private implicit val parsedMapWrites: Writes[Map[BlobId, CalendarEventParsedList]] = mapWrites[BlobId, CalendarEventParsedList](s => s.value.value, calendarEventParsedListWrites)

  private implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]
  private implicit val calendarEventParseResponseWrites: Writes[CalendarEventParseResponse] = Json.writes[CalendarEventParseResponse]
  private implicit val calendarEventParseRequestReads: Reads[CalendarEventParseRequest] = Json.reads[CalendarEventParseRequest]

  def deserializeCalendarEventParseRequest(input: JsValue): JsResult[CalendarEventParseRequest] = Json.fromJson[CalendarEventParseRequest](input)
  def serializeCalendarEventResponse(calendarEventResponse: CalendarEventParseResponse, properties: Properties): JsValue = {
    val originalJson = Json.toJson(calendarEventResponse)

    val filteredParsedOptional: JsResult[Map[String, JsValue]] = originalJson.transform((__ \ "parsed").json.pick[JsObject])
      .map(jsObject => jsObject.value
        .map(blobIdToCalendarEvents => blobIdToCalendarEvents._1 -> blobIdToCalendarEvents._2.transform {
          case jsArray: JsArray => JsSuccess(JsArray(jsArray.value
            .map[JsValue] {
              case calendarEventJsObject: JsObject => properties.filter(calendarEventJsObject)
            }))
          case js => JsSuccess(js)
        }.fold(_ => JsObject(Map(blobIdToCalendarEvents)), o => o))
        .toMap)

    filteredParsedOptional.map(
      filterParsed => {
        originalJson.transform((__ \ "parsed").json.prune).get.transform(
          (__).json.update(__.read[JsObject].map(prunedParsed => prunedParsed ++ JsObject(Map("parsed" -> JsObject(filterParsed))))))
      })
      .map(_.get)
      .getOrElse(originalJson)
  }
}
