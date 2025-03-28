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

package com.linagora.tmail.james.jmap.calendar

import java.io.ByteArrayInputStream
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util
import java.util.UUID

import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier.{ImplicitCalendar, ImplicitVEvent, NoUpdateRequiredException}
import com.linagora.tmail.james.jmap.calendar.{CalendarEventModifier => testee}
import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.{Calendar, Component, Property}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{Nested, Test}

import scala.jdk.CollectionConverters._

class CalendarEventModifierTest {
  val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")
  val START_DATE_SAMPLE_VALUE = "20250320T150000Z"
  val END_DATE_SAMPLE_VALUE = "20250320T160000Z"
  val START_DATE_SAMPLE: ZonedDateTime = ZonedDateTime.parse(START_DATE_SAMPLE_VALUE, DATE_TIME_FORMATTER)
  val END_DATE_SAMPLE: ZonedDateTime = ZonedDateTime.parse(END_DATE_SAMPLE_VALUE, DATE_TIME_FORMATTER)

  val SAMPLE_CALENDAR: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
    s"""
       |BEGIN:VCALENDAR
       |VERSION:2.0
       |PRODID:-//Example Corp//NONSGML Event//EN
       |METHOD:REQUEST
       |BEGIN:VEVENT
       |UID:123456789@example.com
       |DTSTAMP:20250318T120000Z
       |DTSTART:$START_DATE_SAMPLE_VALUE
       |DTEND:$END_DATE_SAMPLE_VALUE
       |SUMMARY:Project Kickoff Meeting
       |DESCRIPTION:Discuss project scope and deliverables.
       |LOCATION:Conference Room A
       |ORGANIZER:mailto:organizer@example.com
       |ATTENDEE;CN=John Doe;RSVP=TRUE:mailto:johndoe@example.com
       |ATTENDEE;CN=Jane Smith;RSVP=TRUE:mailto:janesmith@example.com
       |SEQUENCE:2
       |END:VEVENT
       |END:VCALENDAR
       |
       |""".stripMargin.getBytes("UTF-8")))

  @Test
  def shouldChangeDTSTARTWhenStartDateAreDifferent(): Unit = {
    val newStartDateTime: ZonedDateTime = START_DATE_SAMPLE.minusHours(1)

    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(newStartDateTime, END_DATE_SAMPLE))
      .apply(SAMPLE_CALENDAR)

    assertThat(CalendarEventParsed.from(newCalendar).head.start.get.value)
      .isEqualTo(newStartDateTime)
  }

  @Test
  def shouldChangeDTENDWhenEndDateAreDifferent: Unit = {
    val newEndDateTime: ZonedDateTime = END_DATE_SAMPLE.plusHours(1)

    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE, newEndDateTime))
      .apply(SAMPLE_CALENDAR)

    assertThat(CalendarEventParsed.from(newCalendar).head.end.get.value)
      .isEqualTo(newEndDateTime)
  }

  @Test
  def shouldThrowWhenStartDateAndEndDateAreTheSame(): Unit = {
    assertThatThrownBy(() => testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE, END_DATE_SAMPLE))
      .apply(SAMPLE_CALENDAR))
      .isInstanceOf(classOf[NoUpdateRequiredException])
  }

  @Test
  def shouldIncrementSequenceWhenChangingEvent(): Unit = {
    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE.minusHours(1), END_DATE_SAMPLE))
      .apply(SAMPLE_CALENDAR)
    
    val parsedNewCalendar: CalendarEventParsed = CalendarEventParsed.from(newCalendar).head

    assertThat(parsedNewCalendar.sequence.get.value)
      .isEqualTo(3)
  }

  @Test
  def shouldAddSequenceDefaultWhenAbsentSEQUENCE(): Unit = {
    val absentSequenceCalendar: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      s"""
         |BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Example Corp//NONSGML Event//EN
         |METHOD:REQUEST
         |BEGIN:VEVENT
         |UID:123456789@example.com
         |DTSTAMP:20250318T120000Z
         |DTSTART:$START_DATE_SAMPLE_VALUE
         |DTEND:$END_DATE_SAMPLE_VALUE
         |SUMMARY:Project Kickoff Meeting
         |DESCRIPTION:Discuss project scope and deliverables.
         |LOCATION:Conference Room A
         |ORGANIZER:mailto:organizer@example.com
         |ATTENDEE;CN=John Doe;RSVP=TRUE:mailto:johndoe@example.com
         |ATTENDEE;CN=Jane Smith;RSVP=TRUE:mailto:janesmith@example.com
         |END:VEVENT
         |END:VCALENDAR
         |
         |""".stripMargin.getBytes("UTF-8")))

    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE.minusHours(1), END_DATE_SAMPLE))
      .apply(absentSequenceCalendar)

    assertThat(CalendarEventParsed.from(newCalendar).head.sequence.get.value)
      .isEqualTo(CalendarEventModifier.MODIFIED_SEQUENCE_DEFAULT.getSequenceNo)
  }

  @Test
  def shouldChangeDTSTAMPWhenChangingEvent(): Unit = {
    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE.minusHours(1), END_DATE_SAMPLE))
      .apply(SAMPLE_CALENDAR)
    
    assertThat(newCalendar.getComponent[VEvent](Component.VEVENT).get().getDateTimeStamp.getValue)
      .isNotEqualTo("20250318T120000Z")
  }

  @Test
  def shouldKeepOtherPropertiesWhenChangingEvent(): Unit = {
    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE.minusHours(1), END_DATE_SAMPLE))
      .apply(SAMPLE_CALENDAR)

    // ignore DTSTAMP
    assertThat(newCalendar.toString.replaceAll("(?m)^DTSTAMP:.*\\R?", "").trim.stripMargin)
      .isEqualToNormalizingNewlines(
        """BEGIN:VCALENDAR
          |VERSION:2.0
          |PRODID:-//Example Corp//NONSGML Event//EN
          |METHOD:REQUEST
          |BEGIN:VEVENT
          |UID:123456789@example.com
          |DTSTART;TZID=UTC:20250320T140000
          |DTEND;TZID=UTC:20250320T160000
          |SUMMARY:Project Kickoff Meeting
          |DESCRIPTION:Discuss project scope and deliverables.
          |LOCATION:Conference Room A
          |ORGANIZER:mailto:organizer@example.com
          |ATTENDEE;CN=John Doe;RSVP=TRUE:mailto:johndoe@example.com
          |ATTENDEE;CN=Jane Smith;RSVP=TRUE:mailto:janesmith@example.com
          |SEQUENCE:3
          |END:VEVENT
          |END:VCALENDAR""".trim.stripMargin)
  }

  @Test
  def shouldAddDTENDWhenAbsent(): Unit = {
    val absentDTSTARTCalendar: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      s"""
         |BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Example Corp//NONSGML Event//EN
         |METHOD:REQUEST
         |BEGIN:VEVENT
         |UID:123456789@example.com
         |DTSTAMP:20250318T120000Z
         |DTSTART:$START_DATE_SAMPLE_VALUE
         |SUMMARY:Project Kickoff Meeting
         |DESCRIPTION:Discuss project scope and deliverables.
         |LOCATION:Conference Room A
         |ORGANIZER:mailto:organizer@example.com
         |ATTENDEE;CN=John Doe;RSVP=TRUE:mailto:johndoe@example.com
         |ATTENDEE;CN=Jane Smith;RSVP=TRUE:mailto:janesmith@example.com
         |END:VEVENT
         |END:VCALENDAR
         |
         |""".stripMargin.getBytes("UTF-8")))

    val newEndDateTime = END_DATE_SAMPLE.plusHours(1)
    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE, newEndDateTime))
      .apply(absentDTSTARTCalendar) 
    
    assertThat(CalendarEventParsed.from(newCalendar).head.end.get.value)
      .isEqualTo(newEndDateTime)
  }

  @Test
  def shouldRemoveDURATIONWhenUpdatingEventWithoutDTEND(): Unit = {
    // given calendar with absent DTEND, but present DURATION
    val originalCalendar: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      s"""
         |BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Example Corp//NONSGML Event//EN
         |METHOD:REQUEST
         |BEGIN:VEVENT
         |UID:123456789@example.com
         |DTSTAMP:20250318T120000Z
         |DTSTART:$START_DATE_SAMPLE_VALUE
         |DURATION:PT4H
         |SUMMARY:Project Kickoff Meeting
         |DESCRIPTION:Discuss project scope and deliverables.
         |LOCATION:Conference Room A
         |ORGANIZER:mailto:organizer@example.com
         |ATTENDEE;CN=John Doe;RSVP=TRUE:mailto:johndoe@example.com
         |ATTENDEE;CN=Jane Smith;RSVP=TRUE:mailto:janesmith@example.com
         |END:VEVENT
         |END:VCALENDAR
         |
         |""".stripMargin.getBytes("UTF-8")))

    // When updating event with new end date
    val newCalendar = testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE, END_DATE_SAMPLE.plusHours(1)))
      .apply(originalCalendar)
    // Then DURATION should be removed
    assertThat(newCalendar.toString)
      .doesNotContain("DURATION")
  }

  @Test
  def shouldThrowWhenNewEndDateIsBeforeStartDate(): Unit = {
    assertThatThrownBy(() => testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE, START_DATE_SAMPLE.minusHours(1)))
      .apply(SAMPLE_CALENDAR))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def shouldThrowWhenSameDateButDifferentTimeZones(): Unit = {
    val vietNamZone = ZoneId.of("Asia/Ho_Chi_Minh")

    assertThatThrownBy(() => testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE.withZoneSameInstant(vietNamZone),
        END_DATE_SAMPLE.withZoneSameInstant(vietNamZone)))
      .apply(SAMPLE_CALENDAR))
      .isInstanceOf(classOf[NoUpdateRequiredException])
  }

  @Test
  def shouldKeepOriginalTZIDWhenChangingEvent(): Unit = {
    val originalCalendar: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      """BEGIN:VCALENDAR
        |VERSION:2.0
        |PRODID:-//Sabre//Sabre VObject 4.1.3//EN
        |BEGIN:VTIMEZONE
        |TZID:Asia/Jakarta
        |BEGIN:STANDARD
        |TZOFFSETFROM:+0700
        |TZOFFSETTO:+0700
        |TZNAME:WIB
        |DTSTART:19700101T000000
        |END:STANDARD
        |END:VTIMEZONE
        |BEGIN:VEVENT
        |UID:a83510e3-df88-48cd-8f04-32d0683fa1cd
        |TRANSP:OPAQUE
        |DTSTART;TZID=Asia/Jakarta:20250320T130000
        |DTEND;TZID=Asia/Jakarta:20250320T150000
        |CLASS:PUBLIC
        |SUMMARY:Test1
        |ORGANIZER;CN=John1 Doe1:mailto:user1@open-paas.org
        |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI
        | DUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.1:mailto:user2@open-paas.org
        |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:u
        | ser1@open-paas.org
        |DTSTAMP:20250319T045814Z
        |END:VEVENT
        |END:VCALENDAR
        |""".stripMargin.getBytes("UTF-8")))

    val updatedCalendar = testee.of(CalendarEventTimingUpdatePatch(
      START_DATE_SAMPLE.minusHours(1).withZoneSameInstant(ZoneId.of("UTC")), END_DATE_SAMPLE.withZoneSameInstant(ZoneId.of("UTC"))))
      .apply(originalCalendar).toString

    assertThat(updatedCalendar)
      .contains("DTSTART;TZID=Asia/Jakarta:20250320T213000")

    assertThat(updatedCalendar)
      .contains("DTEND;TZID=Asia/Jakarta:20250320T233000")
  }

  @Test
  def shouldThrowWhenNoVEventPresent(): Unit = {
    val noVEventCalendar: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      """
        |BEGIN:VCALENDAR
        |VERSION:2.0
        |END:VCALENDAR
        |""".stripMargin.getBytes("UTF-8")))

    assertThatThrownBy(() => testee.of(CalendarEventTimingUpdatePatch(START_DATE_SAMPLE,
        END_DATE_SAMPLE))
      .apply(noVEventCalendar))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Nested
  class LocationPatch {
    @Test
    def shouldChangeLOCATIONWhenProposeLocationAreDifferent(): Unit = {
      val newLocation = "newLocation" + UUID.randomUUID()
      val newCalendar = testee.of(LocationUpdatePatch(newLocation)).apply(SAMPLE_CALENDAR)
      assertThat(CalendarEventParsed.from(newCalendar).head.location.get.value).isEqualTo(newLocation)
    }

    @Test
    def shouldThrowWhenNotChangeLOCATION(): Unit = {
      val newLocation = "newLocation" + UUID.randomUUID()
      val calendarAfterUpdateLocationFirstTime = testee.of(LocationUpdatePatch(newLocation)).apply(SAMPLE_CALENDAR)

      assertThatThrownBy(() => testee.of(LocationUpdatePatch(newLocation)).apply(calendarAfterUpdateLocationFirstTime))
        .isInstanceOf(classOf[NoUpdateRequiredException])
    }

    @Test
    def shouldAddLOCATIONWhenNoLocationExists(): Unit = {
      val modifiedCalendar = SAMPLE_CALENDAR.copy()
      modifiedCalendar.getFirstVEvent.removeProperty(Property.LOCATION)

      val newLocation: String = "newLocation" + UUID.randomUUID()

      val updatedCalendar = testee.of(LocationUpdatePatch(newLocation)).apply(modifiedCalendar)
      assertThat(CalendarEventParsed.from(updatedCalendar).head.location.get.value).isEqualTo(newLocation)
    }

    @Test
    def shouldNotModifyLOCATIONWhenProposedLocationIsSame(): Unit = {
      val originalLocation = CalendarEventParsed.from(SAMPLE_CALENDAR).head.location.get.value

      assertThatThrownBy(() => testee.of(LocationUpdatePatch(originalLocation)).apply(SAMPLE_CALENDAR))
        .isInstanceOf(classOf[NoUpdateRequiredException])
    }
  }

  @Nested
  class AttendeePatch {
    @Test
    def shouldAddNewAttendeeWhenNotInOriginalList(): Unit = {
      val newAttendee = new Attendee(s"mailto:${UUID.randomUUID().toString}@example.com")
      val updatedCalendar = testee.of(AttendeeUpdatePatch(Seq(newAttendee))).apply(SAMPLE_CALENDAR)

      assertThat(updatedCalendar.toString)
        .contains(newAttendee.toString)
    }

    @Test
    def shouldNotModifyAttendeesWhenAllExist(): Unit = {
      val existingAttendees = SAMPLE_CALENDAR.getFirstVEvent.getAttendees.asScala.toSeq

      assertThatThrownBy(() => testee.of(AttendeeUpdatePatch(existingAttendees)).apply(SAMPLE_CALENDAR))
        .isInstanceOf(classOf[NoUpdateRequiredException])
    }

    @Test
    def shouldAddMultipleNewAttendees(): Unit = {
      val attendee1 = new Attendee("mailto:attendee1@example.com")
      val attendee2 = new Attendee("mailto:attendee2@example.com")

      val updatedCalendar = testee.of(AttendeeUpdatePatch(Seq(attendee1, attendee2))).apply(SAMPLE_CALENDAR)
        .toString

      assertThat(updatedCalendar)
        .contains(attendee1.toString)

      assertThat(updatedCalendar)
        .contains(attendee2.toString)
    }

    @Test
    def shouldRespectCurrentAttendees(): Unit = {
      val currentAttendees: util.List[Attendee] = SAMPLE_CALENDAR.getFirstVEvent.getAttendees
      val newAttendee = new Attendee(s"mailto:${UUID.randomUUID().toString}@example.com")
      val updatedCalendar = testee.of(AttendeeUpdatePatch(Seq(newAttendee))).apply(SAMPLE_CALENDAR)

      assertThat(updatedCalendar.getFirstVEvent.getAttendees.size())
        .isEqualTo(currentAttendees.size() + 1)
      assertThat(updatedCalendar.getFirstVEvent.getAttendees).containsAll(currentAttendees)
    }
  }

  implicit class ImplicitCalendarString(value: String) {
    def removeDTSTAMPLines(): String =
      value.replaceAll("(?m)^DTSTAMP:.*\\R?", "").trim.stripMargin
  }
}