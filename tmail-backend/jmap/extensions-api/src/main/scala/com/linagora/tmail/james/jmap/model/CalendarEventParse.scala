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

import java.io.InputStream
import java.time.temporal.Temporal
import java.time.{Duration, _}
import java.util.{Locale, Optional, TimeZone}

import com.google.common.base.Preconditions
import com.ibm.icu.util.{TimeZone => Icu4jTimeZone}
import com.linagora.tmail.james.jmap.AttendanceStatus
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier.ImplicitCalendar
import com.linagora.tmail.james.jmap.model.CalendarEventParse.UnparsedBlobId
import com.linagora.tmail.james.jmap.model.CalendarEventStatusField.EventStatus
import com.linagora.tmail.james.jmap.model.CalendarFreeBusyStatusField.FreeBusyStatus
import com.linagora.tmail.james.jmap.model.CalendarPrivacyField.CalendarPrivacy
import com.linagora.tmail.james.jmap.model.RecurrenceRulesField.parseRecurrenceRules
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import net.fortuna.ical4j.data.{CalendarBuilder, CalendarParserFactory, ContentHandlerContext}
import net.fortuna.ical4j.model.Recur.Skip
import net.fortuna.ical4j.model.WeekDay.Day
import net.fortuna.ical4j.model._
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.TzId
import net.fortuna.ical4j.model.property._
import net.fortuna.ical4j.transform.recurrence.Frequency
import net.fortuna.ical4j.util.CompatibilityHints
import org.apache.james.core.MailAddress
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.UnsignedInt.UnsignedInt
import org.apache.james.jmap.core._
import org.apache.james.jmap.mail.BlobId

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.language.implicitConversions
import scala.util.Try

object CalendarEventParse {
  type UnparsedBlobId = String Refined Id.IdConstraint

  val defaultProperties: Properties = Properties("uid", "title", "description", "start", "end", "utcStart", "utcEnd", "duration", "timeZone",
    "location", "method", "sequence", "privacy", "priority", "freeBusyStatus", "status", "organizer", "participants",
    "recurrenceRules", "excludedRecurrenceRules", "extensionFields")

  val allowedProperties: Properties = Properties("uid", "title", "description", "start", "end", "utcStart", "utcEnd", "duration", "timeZone",
    "location", "method", "sequence", "privacy", "priority", "freeBusyStatus", "status", "organizer", "participants",
    "recurrenceRules", "excludedRecurrenceRules", "extensionFields")
}

case class CalendarEventParsedList(value: List[CalendarEventParsed])

case class CalendarEventNotDone(value: Map[UnparsedBlobId, SetError]) {
  def merge(other: CalendarEventNotDone): CalendarEventNotDone = CalendarEventNotDone(this.value ++ other.value)
}

case class CalendarEventNotFound(value: Set[UnparsedBlobId]) {
  def merge(other: CalendarEventNotFound): CalendarEventNotFound = CalendarEventNotFound(this.value ++ other.value)
}

case class CalendarEventNotParsable(value: Set[UnparsedBlobId]) {
  def merge(other: CalendarEventNotParsable): CalendarEventNotParsable = CalendarEventNotParsable(this.value ++ other.value)

  def asSetErrorMap: Map[UnparsedBlobId, SetError] =
    value.map(unparseBlobId => unparseBlobId -> SetError.invalidArguments(SetErrorDescription("Can not parse blobId")))
      .toMap
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

object CalendarMethodField {
  def from(calendar: Calendar): Option[CalendarMethodField] =
    Option(calendar.getMethod).map(_.getValue).map(CalendarMethodField(_))
}

case class CalendarMethodField(value: String) extends AnyVal

object CalendarSequenceField {
  def from(vEvent: VEvent): Option[CalendarSequenceField] =
    Option(vEvent.getSequence).map(_.getSequenceNo).map(CalendarSequenceField(_))
}

case class CalendarSequenceField(value: Int) extends AnyVal

object CalendarUidField {
  def from(vEvent: VEvent): Option[CalendarUidField] =
    vEvent.getUid.toScala
      .map(_.getValue).map(CalendarUidField(_))

  def getEventUidFromCalendar(calendar: Calendar): String =
    from(calendar.getFirstVEvent) match {
      case Some(uidField) => uidField.value
      case None => throw new IllegalStateException("Calendar does not contain UID field")
    }
}

case class CalendarUidField(value: String) extends AnyVal

object CalendarPriorityField {
  def from(vEvent: VEvent): Option[CalendarPriorityField] =
    Option(vEvent.getPriority).map(_.getLevel).map(CalendarPriorityField(_))
}

case class CalendarPriorityField(value: Int) extends AnyVal

object CalendarFreeBusyStatusField extends Enumeration {
  type FreeBusyStatus = Value
  private val Free = Value("free")
  private val Busy = Value("busy")

  def from(vEvent: VEvent): Option[CalendarFreeBusyStatusField] =
    Option(vEvent.getTimeTransparency)
      .map(transparency => transparency.getValue match {
        case Transp.VALUE_OPAQUE => Busy
        case Transp.VALUE_TRANSPARENT => Free
      })
      .map(CalendarFreeBusyStatusField(_))
}

case class CalendarFreeBusyStatusField(value: FreeBusyStatus) extends AnyVal

object CalendarEventStatusField extends Enumeration {
  type EventStatus = Value
  val Confirmed = Value("confirmed")
  val Cancelled = Value("cancelled")
  val Tentative = Value("tentative")

  def from(vEvent: VEvent): Option[CalendarEventStatusField] =
    Option(vEvent.getStatus)
      .map(status => status.getValue match {
        case Status.VALUE_CONFIRMED => Confirmed
        case Status.VALUE_CANCELLED => Cancelled
        case Status.VALUE_TENTATIVE => Tentative
      })
      .map(CalendarEventStatusField(_))
}

case class CalendarEventStatusField(value: EventStatus) extends AnyVal

object CalendarPrivacyField extends Enumeration {
  type CalendarPrivacy = Value
  private val Public = Value("public")
  private val Private = Value("private")
  private val Secret = Value("secret")

  def from(vEvent: VEvent): Option[CalendarPrivacyField] =
    Option(vEvent.getClassification)
      .map(accessClassification => accessClassification.getValue match {
        case Clazz.VALUE_PUBLIC => Public
        case Clazz.VALUE_PRIVATE => Private
        case Clazz.VALUE_CONFIDENTIAL => Secret
      })
      .map(CalendarPrivacyField(_))
}

case class CalendarPrivacyField(value: CalendarPrivacy) extends AnyVal

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
    Option(calendarEvent.getDateTimeStart[Temporal]())
      .map(startDate => startDate.getDate)
      .flatMap(temporal => VEventTemporalUtil.temporalToZonedDateTime(temporal, VEventTemporalUtil.getAlternativeZoneId(calendarEvent)))
      .map(CalendarStartField(_))
}

case class CalendarStartField(value: ZonedDateTime) extends AnyVal {
  def asUtcDate(): UTCDate = UTCDate(value)
}

object CalendarEndField {
  def from(calendarEvent: VEvent): Option[CalendarEndField] =
    calendarEvent.getEndDate[Temporal]().toScala
      .map(endDate => endDate.getDate)
      .flatMap(temporal => VEventTemporalUtil.temporalToZonedDateTime(temporal, VEventTemporalUtil.getAlternativeZoneId(calendarEvent)))
      .map(CalendarEndField(_))
}

object VEventTemporalUtil {

  def temporalToZonedDateTime(temporal: Temporal, zoneIdDefault: ZoneId = ZoneId.systemDefault()): Option[ZonedDateTime] = temporal match {
    case zdt: ZonedDateTime => Some(zdt)
    case odt: OffsetDateTime => Some(odt.atZoneSameInstant(zoneIdDefault))
    case ldt: LocalDateTime => Some(ldt.atZone(zoneIdDefault))
    case localDate: LocalDate => Some(localDate.atStartOfDay(zoneIdDefault))
    case instant: Instant => Some(instant.atZone(zoneIdDefault))
    case _ => None
  }

  def getAlternativeZoneId(calendarEvent: VEvent): ZoneId =
    CalendarTimeZoneField.getZoneIdFromTZID(calendarEvent)
      .getOrElse(ZoneId.of("UTC"))
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
    getZoneIdFromTZID(calendarEvent)
      .orElse(getZoneIdFromStartDate(calendarEvent))
      .map(zoneId => CalendarTimeZoneField(TimeZone.getTimeZone(zoneId)))

  def getZoneIdFromTZID(calendarEvent: VEvent): Option[ZoneId] =
   calendarEvent.getProperty[Property](Property.TZID).toScala
      .map(_.getValue)
      .flatMap(string => extractZoneId(string))

  def getZoneIdFromStartDate(calendarEvent: VEvent): Option[ZoneId] =
    Option(calendarEvent.getDateTimeStart[Temporal]())
      .flatMap(date => date.getParameter[TzId](Parameter.TZID).toScala)
      .map(_.getValue)
      .flatMap(string => extractZoneId(string))

  def extractZoneId(value: String): Option[ZoneId] = {
    def parseIANAZone: ZoneId =
      ZoneId.of(value)

    def parseWindowZone(value: String): Option[ZoneId] =
      Option(Icu4jTimeZone.getIDForWindowsID(value, "US"))
        .map(value => ZoneId.of(value))

    Try(parseIANAZone)
      .fold(_ => parseWindowZone(value),
        ianaZoneId => Some(ianaZoneId))
  }
}

case class CalendarTimeZoneField(value: TimeZone) extends AnyVal

object CalendarOrganizerField {
  def from(calendarEvent: VEvent): Option[CalendarOrganizerField] =
    Option(calendarEvent.getOrganizer)
      .map(organizer =>
        CalendarOrganizerField(name = organizer.getParameter[Parameter]("CN").toScala
          .map(_.getValue),
          mailto = Option(organizer.getCalAddress)
            .map(_.getSchemeSpecificPart)
            .map(new MailAddress(_))))
}
case class CalendarOrganizerField(name: Option[String], mailto: Option[MailAddress]) {
  def asMailAddressString(): Option[String] = mailto.map(_.asString())
}

object CalendarAttendeeName {
  private val ATTENDEE_PARAMETER_PRIMARY: String = "NAME"
  private val ATTENDEE_PARAMETER_SECONDARY: String = "CN"

  def from(attendee: Attendee): Option[CalendarAttendeeName] =
    attendee.getParameter[Parameter](ATTENDEE_PARAMETER_PRIMARY).toScala
      .orElse(attendee.getParameter[Parameter](ATTENDEE_PARAMETER_SECONDARY).toScala)
      .map(_.getValue)
      .map(CalendarAttendeeName(_))
}
case class CalendarAttendeeName(value: String) extends AnyVal
object CalendarAttendeeKind {
  def from(attendee: Attendee): Option[CalendarAttendeeKind] =
    attendee.getParameter[Parameter](Parameter.CUTYPE).toScala
      .map(_.getValue)
      .map(CalendarAttendeeKind(_))
}
case class CalendarAttendeeKind(value: String) extends AnyVal
object CalendarAttendeeRole {
  def from(attendee: Attendee): Option[CalendarAttendeeRole] =
    attendee.getParameter[Parameter](Parameter.ROLE).toScala
      .map(_.getValue)
      .map(CalendarAttendeeRole(_))
}
case class CalendarAttendeeRole(value: String) extends AnyVal
object CalendarAttendeeParticipationStatus {
  def from(attendee: Attendee): Option[CalendarAttendeeParticipationStatus] =
   attendee.getParameter[Parameter](Parameter.PARTSTAT).toScala
      .map(_.getValue)
      .map(CalendarAttendeeParticipationStatus(_))
}
case class CalendarAttendeeParticipationStatus(value: String)
object CalendarAttendeeExpectReply {
  def from(attendee: Attendee): Option[CalendarAttendeeExpectReply] =
    attendee.getParameter[Parameter](Parameter.RSVP).toScala
      .map(_.getValue)
      .map(_.toBoolean)
      .map(CalendarAttendeeExpectReply(_))
}
case class CalendarAttendeeExpectReply(value: Boolean) extends AnyVal

object CalendarAttendeeMailTo {
  def from(attendee: Attendee): Option[CalendarAttendeeMailTo] =
    Option(attendee.getCalAddress)
      .map(_.getSchemeSpecificPart)
      .map(new MailAddress(_))
      .map(CalendarAttendeeMailTo(_))
}
case class CalendarAttendeeMailTo(value: MailAddress) extends AnyVal {
  def serialize(): String = value.toString
}

object CalendarAttendeeField {
  def from(attendee: Attendee): CalendarAttendeeField = {
    CalendarAttendeeField(name = CalendarAttendeeName.from(attendee),
      mailto = CalendarAttendeeMailTo.from(attendee),
      kind = CalendarAttendeeKind.from(attendee),
      role = CalendarAttendeeRole.from(attendee),
      participationStatus = CalendarAttendeeParticipationStatus.from(attendee),
      expectReply = CalendarAttendeeExpectReply.from(attendee))
  }
}

case class CalendarAttendeeField(name: Option[CalendarAttendeeName] = None,
                                 mailto: Option[CalendarAttendeeMailTo] = None,
                                 kind: Option[CalendarAttendeeKind] = None,
                                 role: Option[CalendarAttendeeRole] = None,
                                 participationStatus: Option[CalendarAttendeeParticipationStatus] = None,
                                 expectReply: Option[CalendarAttendeeExpectReply] = None)

object CalendarParticipantsField {
  def from(vevent: VEvent): CalendarParticipantsField =
    CalendarParticipantsField(vevent.getProperties(Property.ATTENDEE)
      .asScala
      .map(attendee => CalendarAttendeeField.from(attendee))
      .toSeq)
}
case class CalendarParticipantsField(list: Seq[CalendarAttendeeField] = Seq()) {

  def findParticipantByMailTo(mailto: String): Option[CalendarAttendeeField] =
    list.find(_.mailto.exists(_.serialize() == mailto))
}

object CalendarExtensionFields {
  private val EXTENSION_FIELD_PREFIX: String = "X-"

  def from(vevent: VEvent): CalendarExtensionFields =
    CalendarExtensionFields(vevent.getProperties[Property]().asScala
      .filter(property => property.getName.toLowerCase(Locale.US).startsWith(EXTENSION_FIELD_PREFIX.toLowerCase(Locale.US)))
      .map(property => (property.getName, property.getValue))
      .groupMap(_._1)(_._2)
      .map(pair => (pair._1, pair._2.toSeq)))
}

case class CalendarExtensionFields(values: Map[String, Seq[String]] = Map())

object NumberListUtils {
  implicit class SeqImprovements(val numberList: NumberList) {
    def getValue: Option[Seq[Int]] =
      if (numberList.isEmpty) {
        None
      } else {
        Some(numberList.asScala.toSeq.map(_.intValue()))
      }
  }
}
object RecurrenceRuleFrequency {
  def from(recur: Recur[Temporal]): RecurrenceRuleFrequency = RecurrenceRuleFrequency(recur.getFrequency)
}

case class RecurrenceRuleFrequency(value: Frequency) extends AnyVal

object RecurrenceRuleRScale {
  def from(recur: Recur[Temporal]): Option[RecurrenceRuleRScale] = {
    val tokens: Iterator[String] = recur.toString.split("[;=]").iterator
    var rscale: Option[RecurrenceRuleRScale] = None
    while (tokens.hasNext) {
      if ("RSCALE".equals(tokens.next())) {
        rscale = Option(tokens.next())
          .map(RecurrenceRuleRScale(_))
      }
    }
    rscale
  }
}

case class RecurrenceRuleRScale(value: String) extends AnyVal

object CalendarEventByMonth {
  def from(recur: Recur[Temporal]): Option[CalendarEventByMonth] =
    if (recur.getMonthList.isEmpty) {
      None
    } else {
      Option(recur.getMonthList)
        .map(_.asScala)
        .map(_.toSeq)
        .map(CalendarEventByMonth(_))
    }
}

case class CalendarEventByMonth(value: Seq[net.fortuna.ical4j.model.Month])

object CalendarEventByDay {
  def from(recur: Recur[Temporal]): Option[CalendarEventByDay] =
    if (recur.getDayList.isEmpty) {
      None
    } else {
      Option(recur.getDayList)
        .map(_.asScala)
        .map(dayList => dayList.map(_.getDay).toSeq)
        .map(CalendarEventByDay(_))
    }
}

case class CalendarEventByDay(value: Seq[Day])

object RecurrenceRuleCount{
  def from(recur: Recur[Temporal]) : Option[RecurrenceRuleCount] =
    recur.getCount match {
      case c if c > 0 => Some(from(c))
      case _ => None
    }

  def from(value: Int): RecurrenceRuleCount = RecurrenceRuleCount(UnsignedInt.liftOrThrow(value))
}
case class RecurrenceRuleCount(value: UnsignedInt)

object RecurrenceRuleInterval {
  def from(recur: Recur[Temporal]): Option[RecurrenceRuleInterval] =
    recur.getInterval match {
      case i if i > 0 => Some(from(i))
      case _ => None
    }

  def from(value: Int): RecurrenceRuleInterval = RecurrenceRuleInterval(UnsignedInt.liftOrThrow(value))
}
case class RecurrenceRuleInterval(value: UnsignedInt)

object RecurrenceRuleUntil {
  def from(recur: Recur[Temporal]): Option[RecurrenceRuleUntil] =
    Option(recur.getUntil)
      .flatMap(date => VEventTemporalUtil.temporalToZonedDateTime(date, ZoneId.of("UTC")))
      .map(zoneDateTime => RecurrenceRuleUntil(new UTCDate(zoneDateTime)))
}

case class RecurrenceRuleUntil(value: UTCDate)

object RecurrenceRulesField {
  import scala.jdk.CollectionConverters._

  def from(vevent: VEvent): RecurrenceRulesField =
    RecurrenceRulesField(vevent.getProperties[RRule[Temporal]](Property.RRULE)
      .asScala
      .map(rrule => parseRecurrenceRules(rrule.getRecur))
      .toSeq)

  def parseRecurrenceRules(recur: Recur[Temporal]): RecurrenceRule = {
    val frequency: Frequency = recur.getFrequency
    RecurrenceRule(
      frequency = RecurrenceRuleFrequency(frequency),
      until = RecurrenceRuleUntil.from(recur),
      count = RecurrenceRuleCount.from(recur),
      interval = RecurrenceRuleInterval.from(recur),
      rscale = RecurrenceRuleRScale.from(recur),
      skip = Option(recur.getSkip),
      firstDayOfWeek = Option(recur.getWeekStartDay).map(_.getDay),
      byDay = CalendarEventByDay.from(recur),
      byMonth = CalendarEventByMonth.from(recur),
      byMonthDay = convertJavaListToOptionSeq(recur.getMonthDayList),
      byYearDay = convertJavaListToOptionSeq(recur.getYearDayList),
      byWeekNo = convertJavaListToOptionSeq(recur.getWeekNoList),
      byHour = convertJavaListToOptionSeq(recur.getHourList),
      byMinute = convertJavaListToOptionSeq(recur.getMinuteList),
      bySecond = convertJavaListToOptionSeq(recur.getSecondList),
      bySetPosition = convertJavaListToOptionSeq(recur.getSetPosList))
  }

  private def convertJavaListToOptionSeq(javaList: java.util.List[Integer]): Option[Seq[Int]] =
    Option(javaList)
      .filter(_.size() > 0)
      .map(_.asScala.toSeq.map(_.toInt))
}

object ExcludedRecurrenceRulesField {
  def from(vevent: VEvent): ExcludedRecurrenceRulesField =
    ExcludedRecurrenceRulesField(vevent.getProperties[ExRule[Temporal]](Property.EXRULE)
      .asScala
      .map(exRule => parseRecurrenceRules(exRule.getRecur))
      .toSeq)
}

case class RecurrenceRulesField(value: Seq[RecurrenceRule])
case class ExcludedRecurrenceRulesField(value: Seq[RecurrenceRule])
case class RecurrenceRule(frequency: RecurrenceRuleFrequency,
                          until: Option[RecurrenceRuleUntil] = None,
                          count: Option[RecurrenceRuleCount] = None,
                          interval: Option[RecurrenceRuleInterval] = None,
                          rscale: Option[RecurrenceRuleRScale] = None,
                          skip: Option[Skip] = None,
                          firstDayOfWeek: Option[Day] = None,
                          byDay: Option[CalendarEventByDay] = None,
                          byMonthDay: Option[Seq[Int]] = None,
                          byMonth: Option[CalendarEventByMonth] = None,
                          byYearDay: Option[Seq[Int]] = None,
                          byWeekNo: Option[Seq[Int]] = None,
                          byHour: Option[Seq[Int]] = None,
                          byMinute: Option[Seq[Int]] = None,
                          bySecond: Option[Seq[Int]] = None,
                          bySetPosition: Option[Seq[Int]] = None)

object RecurrenceIdField {

  def from(vEvent: VEvent): Option[RecurrenceIdField] =
    Option(vEvent.getRecurrenceId[Temporal]).map(_.getValue)
      .map(RecurrenceIdField(_))

  def getRecurrenceIdAsString(calendar: Calendar): Optional[String] =
    from(calendar.getFirstVEvent).map(_.value).toJava
}

case class RecurrenceIdField(value: String) extends AnyVal {
  def asString(): String = value
}

case class InvalidCalendarFileException(blobId: BlobId, originException: Throwable) extends RuntimeException {
  override def getMessage: String = originException.getMessage
}

object CalendarEventParsed {
  val init : Unit =
     if (Option(System.getProperty(CompatibilityHints.KEY_RELAXED_UNFOLDING, null)).isEmpty) {
       CompatibilityHints.setHintEnabled(CompatibilityHints.KEY_RELAXED_UNFOLDING, true)
     }

  def from(calendarContent: InputStream): List[CalendarEventParsed] =
    from(parseICal4jCalendar(calendarContent))

  def from(calendar: Calendar): List[CalendarEventParsed] = {
    val vEventList: List[VEvent] = calendar.getComponents("VEVENT").asInstanceOf[java.util.List[VEvent]].asScala.toList
    Preconditions.checkArgument(vEventList.nonEmpty, "Calendar does not contain VEVENT component".asInstanceOf[Object])

    vEventList.map(fromVEvent(_, calendar))
  }

  def parseICal4jCalendar(calendarContent: InputStream): Calendar = {
    new CalendarBuilder(CalendarParserFactory.getInstance.get,
      new ContentHandlerContext().withSupressInvalidProperties(true),
      TimeZoneRegistryFactory.getInstance.createRegistry)
      .build(calendarContent)
  }

  private def fromVEvent(vevent: VEvent, calendar: Calendar): CalendarEventParsed = {
    val start: Option[CalendarStartField] = CalendarStartField.from(vevent)
    val end: Option[CalendarEndField] = CalendarEndField.from(vevent)
    CalendarEventParsed(uid = CalendarUidField.from(vevent),
      title = CalendarTitleField.from(vevent),
      description = CalendarDescriptionField.from(vevent),
      start = start,
      end = end,
      utcStart = start.map(_.asUtcDate()),
      utcEnd = end.map(_.asUtcDate()),
      duration = CalendarDurationField.from(vevent),
      timeZone = CalendarTimeZoneField.from(vevent),
      location = CalendarLocationField.from(vevent),
      method = CalendarMethodField.from(calendar),
      sequence = CalendarSequenceField.from(vevent),
      priority = CalendarPriorityField.from(vevent),
      freeBusyStatus = CalendarFreeBusyStatusField.from(vevent),
      status = CalendarEventStatusField.from(vevent),
      privacy = CalendarPrivacyField.from(vevent),
      organizer = CalendarOrganizerField.from(vevent),
      participants = CalendarParticipantsField.from(vevent),
      extensionFields = CalendarExtensionFields.from(vevent),
      recurrenceRules = RecurrenceRulesField.from(vevent),
      excludedRecurrenceRules = ExcludedRecurrenceRulesField.from(vevent),
      recurrenceId = RecurrenceIdField.from(vevent))
  }
}

case class CalendarEventParsed(uid: Option[CalendarUidField] = None,
                               title: Option[CalendarTitleField] = None,
                               description: Option[CalendarDescriptionField] = None,
                               start: Option[CalendarStartField] = None,
                               end: Option[CalendarEndField] = None,
                               utcStart: Option[UTCDate] = None,
                               utcEnd: Option[UTCDate] = None,
                               timeZone: Option[CalendarTimeZoneField] = None,
                               duration: Option[CalendarDurationField] = None,
                               location: Option[CalendarLocationField] = None,
                               method: Option[CalendarMethodField] = None,
                               sequence: Option[CalendarSequenceField] = None,
                               priority: Option[CalendarPriorityField] = None,
                               freeBusyStatus: Option[CalendarFreeBusyStatusField] = None,
                               status: Option[CalendarEventStatusField] = None,
                               privacy: Option[CalendarPrivacyField] = None,
                               organizer: Option[CalendarOrganizerField] = None,
                               participants: CalendarParticipantsField = CalendarParticipantsField(),
                               extensionFields: CalendarExtensionFields = CalendarExtensionFields(),
                               recurrenceRules: RecurrenceRulesField = RecurrenceRulesField(Seq()),
                               excludedRecurrenceRules: ExcludedRecurrenceRulesField = ExcludedRecurrenceRulesField(Seq()),
                               recurrenceId: Option[RecurrenceIdField] = None) {
  def getAttendanceStatus(username: String): Option[AttendanceStatus] = participants.findParticipantByMailTo(username)
    .flatMap(_.participationStatus)
    .map((status: CalendarAttendeeParticipationStatus) => AttendanceStatus.fromCalendarAttendeeParticipationStatus(status).orElseThrow)

  def uidAsString(): Optional[String] = uid.map(_.value).toJava
  def startAsJava(): Optional[ZonedDateTime] = start.map(_.value).toJava
  def endAsJava(): Optional[ZonedDateTime] = end.map(_.value).toJava
  def recurrenceIdAsJava(): Optional[String] = recurrenceId.map(_.value).toJava
}