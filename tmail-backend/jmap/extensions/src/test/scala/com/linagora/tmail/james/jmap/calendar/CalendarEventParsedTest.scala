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

import com.linagora.tmail.james.jmap.model.CalendarEventStatusField.{Cancelled, Confirmed, Tentative}
import com.linagora.tmail.james.jmap.model.{CalendarEventByDay, CalendarEventByMonth, CalendarEventParsed, CalendarEventStatusField, RecurrenceRule, RecurrenceRuleFrequency, RecurrenceRuleInterval}
import net.fortuna.ical4j.model.{Month, WeekDay}
import net.fortuna.ical4j.transform.recurrence.Frequency
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.SoftAssertions
import org.junit.jupiter.api.{Nested, Test}

import scala.jdk.CollectionConverters._

class CalendarEventParsedTest {

  @Test
  def endFieldShouldPreferDTENDWhenPresent(): Unit = {
    val icsPayload = """BEGIN:VCALENDAR
                       |PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR
                       |CALSCALE:GREGORIAN
                       |X-OBM-TIME:1483439571
                       |VERSION:2.0
                       |METHOD:REQUEST
                       |BEGIN:VEVENT
                       |CREATED:20170103T103250Z
                       |LAST-MODIFIED:20170103T103250Z
                       |DTSTAMP:20170103T103250Z
                       |DTSTART:20170120T100000Z
                       |DTEND:20170121T100000Z
                       |DURATION:PT30M
                       |TRANSP:OPAQUE
                       |SEQUENCE:0
                       |SUMMARY:Sprint Social #3 Demo
                       |DESCRIPTION:
                       |CLASS:PUBLIC
                       |PRIORITY:5
                       |ORGANIZER;X-OBM-ID=468;CN=Attendee 1:MAILTO:attendee1@domain.tld
                       | com
                       |X-OBM-DOMAIN:domain.tld
                       |X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922
                       |LOCATION:hangout
                       |CATEGORIES:
                       |X-OBM-COLOR:
                       |UID:f1514f44bf39311568d64072ac247c17656ceafde3b4b3eba961c8c5184cdc6ee047fe
                       | b2aab16e43439a608f28671ab7c10e754c301b1e32001ad51dd20eac2fc7af20abf4093bbe
                       |ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Attendee 2;PARTSTAT=NEEDS-ACTI
                       | ON;X-OBM-ID=348:MAILTO:attendee2@domain.tld
                       |END:VEVENT
                       |END:VCALENDAR
                       |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
      .isEqualTo("2017-01-21T10:00:00Z")
  }

  @Test
  def parseShouldNotFailWhenUnknownTimezoneIdentifier(): Unit = {
    val icsPayload = """BEGIN:VCALENDAR
                       |VERSION:2.0
                       |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
                       |CALSCALE:GREGORIAN
                       |METHOD:REQUEST
                       |BEGIN:VTIMEZONE
                       |TZID:Asia/Ho_Chi_Minh
                       |BEGIN:STANDARD
                       |TZOFFSETFROM:+0700
                       |TZOFFSETTO:+0700
                       |TZNAME:ICT
                       |DTSTART:19700101T000000
                       |END:STANDARD
                       |END:VTIMEZONE
                       |BEGIN:VEVENT
                       |UID:e253f5b5-fbc4-4aa8-81b3-312d8310aedc
                       |TRANSP:OPAQUE
                       |DTSTART;TZID=Asia/Saigon:20250708T120000
                       |DTEND;TZID=Asia/Saigon:20250708T130000
                       |CLASS:PUBLIC
                       |X-OPENPAAS-VIDEOCONFERENCE:
                       |SUMMARY:test counter event
                       |ORGANIZER;CN=John Doe:mailto:johndoe@example.com
                       |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI
                       | DUAL;CN=Jane Roe:mailto:janeroe@example.com
                       |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:jo
                       | hndoe@example.com
                       |DTSTAMP:20250707T040720Z
                       |SEQUENCE:0
                       |END:VEVENT
                       |END:VCALENDAR
                       |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
      .isEqualTo("2025-07-08T13:00:00+07")
  }

  @Test
  def shouldParseStartAndEndTimeOfAllDayEvent(): Unit = {
    val icsPayload = """BEGIN:VCALENDAR
                       |PRODID:-//Google Inc//Google Calendar 70.9054//EN
                       |VERSION:2.0
                       |CALSCALE:GREGORIAN
                       |METHOD:REQUEST
                       |BEGIN:VEVENT
                       |DTSTART;VALUE=DATE:20251112
                       |DTEND;VALUE=DATE:20251113
                       |DTSTAMP:20251110T091903Z
                       |ORGANIZER;CN=Test Organizer:mailto:test.organizer@example.com
                       |UID:uid-1234567890@example.com
                       |ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE;CN=Alice Example;X-NUM-GUESTS=0:mailto:alice@example.com
                       |ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=Bob Example;X-NUM-GUESTS=0:mailto:bob@example.com
                       |X-MICROSOFT-CDO-OWNERAPPTID:1909058852
                       |CREATED:20251110T091900Z
                       |DESCRIPTION:
                       |LAST-MODIFIED:20251110T091900Z
                       |LOCATION:
                       |SEQUENCE:0
                       |STATUS:CONFIRMED
                       |SUMMARY:All day event
                       |TRANSP:TRANSPARENT
                       |END:VEVENT
                       |END:VCALENDAR
                       |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    assertThat(calendarEventParsed.start.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
      .isEqualTo("2025-11-12T00:00:00Z")
    assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
      .isEqualTo("2025-11-13T00:00:00Z")
  }

  @Test
  def multipleVEventCase(): Unit = {
    val icsPayload =
      """BEGIN:VCALENDAR
        |VERSION:2.0
        |PRODID:-//bobbin v0.1//NONSGML iCal Writer//EN
        |CALSCALE:GREGORIAN
        |METHOD:PUBLISH
        |BEGIN:VEVENT
        |DTSTART:20100701T080000Z
        |DTEND:20100701T110000Z
        |DTSTAMP:20091130T213238Z
        |UID:1285935469767a7c7c1a9b3f0df8003a@yoursever.com
        |CREATED:20091130T213238Z
        |DESCRIPTION:Example event 1
        |LAST-MODIFIED:20091130T213238Z
        |SEQUENCE:0
        |STATUS:CONFIRMED
        |SUMMARY:Example event 1
        |TRANSP:OPAQUE
        |END:VEVENT
        |BEGIN:VEVENT
        |DTSTART:20100701T120000Z
        |DTEND:20100701T130000Z
        |DTSTAMP:20091130T213238Z
        |UID:1285935469767a7c7c1a9b3f0df8003b@yoursever.com
        |CREATED:20091130T213238Z
        |DESCRIPTION:Example event 2
        |LAST-MODIFIED:20091130T213238Z
        |SEQUENCE:0
        |STATUS:CONFIRMED
        |SUMMARY:Example event 2
        |TRANSP:OPAQUE
        |END:VEVENT
        |END:VCALENDAR
        |""".stripMargin

    val calendarEventParsedList: List[CalendarEventParsed] = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes()))

    assertThat(calendarEventParsedList.size)
      .isEqualTo(2)
    assertThat(calendarEventParsedList(0).description.get.value)
      .isEqualTo("Example event 1")
    assertThat(calendarEventParsedList(1).description.get.value)
      .isEqualTo("Example event 2")
  }

  @Test
  def endFieldShouldPresentWhenAbsentDTENDAndPresentDTSTARTAndDURATION(): Unit = {
    val icsPayload =
      """BEGIN:VCALENDAR
        |PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR
        |CALSCALE:GREGORIAN
        |X-OBM-TIME:1483439571
        |VERSION:2.0
        |METHOD:REQUEST
        |BEGIN:VEVENT
        |CREATED:20170103T103250Z
        |LAST-MODIFIED:20170103T103250Z
        |DTSTAMP:20170103T103250Z
        |DTSTART:20170120T100000Z
        |DURATION:PT30M
        |TRANSP:OPAQUE
        |SEQUENCE:0
        |SUMMARY:Sprint Social #3 Demo
        |DESCRIPTION:
        |CLASS:PUBLIC
        |PRIORITY:5
        |ORGANIZER;X-OBM-ID=468;CN=Attendee 1:MAILTO:attendee1@domain.tld
        | com
        |X-OBM-DOMAIN:domain.tld
        |X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922
        |LOCATION:hangout
        |CATEGORIES:
        |X-OBM-COLOR:
        |UID:ea127690-0440-404b-af98-9823c855a283
        |ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Attendee 2;PARTSTAT=NEEDS-ACTI
        | ON;X-OBM-ID=348:MAILTO:attendee2@domain.tld
        |END:VEVENT
        |END:VCALENDAR
        |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
      .isEqualTo("2017-01-20T10:30:00Z")
  }

  @Test
  def shouldParseExpectedFields(): Unit = {
    val icsPayload =
      """BEGIN:VCALENDAR
        |PRODID:-//Aliasource Groupe LINAGORA//OBM Calendar 3.2.1-rc2//FR
        |CALSCALE:GREGORIAN
        |X-OBM-TIME:1483439571
        |VERSION:2.0
        |METHOD:REQUEST
        |BEGIN:VEVENT
        |CREATED:20170103T103250Z
        |LAST-MODIFIED:20170103T103250Z
        |DTSTAMP:20170103T103250Z
        |DTSTART:20170120T100000Z
        |DTEND:20170121T100000Z
        |DURATION:PT30M
        |TRANSP:OPAQUE
        |SEQUENCE:0
        |SUMMARY:Sprint Social #3 Demo
        |DESCRIPTION:
        |CLASS:PUBLIC
        |PRIORITY:5
        |ORGANIZER;X-OBM-ID=468;CN=Attendee 1:MAILTO:attendee1@domain.tld
        | com
        |X-OBM-DOMAIN:domain.tld
        |X-OBM-DOMAIN-UUID:02874f7c-d10e-102f-acda-0015176f7922
        |LOCATION:hangout
        |CATEGORIES:
        |X-OBM-COLOR:
        |UID:ea127690-0440-404b-af98-9823c855a283
        |ATTENDEE;CUTYPE=INDIVIDUAL;RSVP=TRUE;CN=Attendee 2;PARTSTAT=NEEDS-ACTI
        | ON;X-OBM-ID=348:MAILTO:attendee2@domain.tld
        |END:VEVENT
        |END:VCALENDAR
        |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    assertThat(calendarEventParsed.method.get.value).isEqualTo("REQUEST")
    assertThat(calendarEventParsed.sequence.get.value).isEqualTo(0)
    assertThat(calendarEventParsed.uid.get.value).isEqualTo("ea127690-0440-404b-af98-9823c855a283")
    assertThat(calendarEventParsed.priority.get.value).isEqualTo(5)
    assertThat(s"${calendarEventParsed.freeBusyStatus.get.value}").isEqualTo("busy")
    assertThat(s"${calendarEventParsed.privacy.get.value}").isEqualTo("public")
  }

  @Test
  def parseWindowTimeZoneShouldSucceed(): Unit = {
    val icsPayload =
      """BEGIN:VCALENDAR
        |METHOD:REQUEST
        |PRODID:Microsoft Exchange Server 2010
        |VERSION:2.0
        |BEGIN:VTIMEZONE
        |TZID:Romance Standard Time
        |BEGIN:STANDARD
        |DTSTART:16010101T030000
        |TZOFFSETFROM:+0200
        |TZOFFSETTO:+0100
        |RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=10
        |END:STANDARD
        |BEGIN:DAYLIGHT
        |DTSTART:16010101T020000
        |TZOFFSETFROM:+0100
        |TZOFFSETTO:+0200
        |RRULE:FREQ=YEARLY;INTERVAL=1;BYDAY=-1SU;BYMONTH=3
        |END:DAYLIGHT
        |END:VTIMEZONE
        |BEGIN:VEVENT
        |ORGANIZER;CN=Bob:mailto:bob@example.com
        |ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=btellier:mailto:btellier@example.com
        |ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;CN=Bob:mailto:bob@example.com
        |DESCRIPTION;LANGUAGE=fr-FR:Petit point cassandra
        |UID:040000008200E00074C5B7101A82E00800000000602312E836EDD901000000000000000
        | 01000000009C40B24EBC55C49B5E76D3C60DFC74F
        |SUMMARY;LANGUAGE=fr-FR:[SV] point cassandra
        |DTSTART;TZID=Romance Standard Time:20230922T160000
        |DTEND;TZID=Romance Standard Time:20230922T170000
        |CLASS:PUBLIC
        |PRIORITY:5
        |DTSTAMP:20230922T072808Z
        |TRANSP:OPAQUE
        |STATUS:CONFIRMED
        |SEQUENCE:0
        |LOCATION;LANGUAGE=fr-FR:Réunion Microsoft Teams
        |X-MICROSOFT-CDO-APPT-SEQUENCE:0
        |BEGIN:VALARM
        |DESCRIPTION:REMINDER
        |TRIGGER;RELATED=START:-PT15M
        |ACTION:DISPLAY
        |END:VALARM
        |END:VEVENT
        |END:VCALENDAR""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    SoftAssertions.assertSoftly(softly => {
      assertThat(calendarEventParsed.timeZone.get.value.getID)
        .isEqualTo("Europe/Paris")
      softly.assertThat(calendarEventParsed.start.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
        .isEqualTo("2023-09-22T16:00:00+02")
      assertThat(calendarEventParsed.utcStart.get.asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
        .isEqualTo("2023-09-22T14:00:00Z")
      assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
        .isEqualTo("2023-09-22T17:00:00+02")
      assertThat(calendarEventParsed.utcEnd.get.asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
        .isEqualTo("2023-09-22T15:00:00Z")
    })
  }

  @Test
  def parseShouldSupportUnfolding(): Unit = {
    val icsPayload =
      s"""BEGIN:VCALENDAR
         |PRODID:-//Google Inc//Google Calendar 70.9054//EN
         |VERSION:2.0
         |CALSCALE:GREGORIAN
         |METHOD:REQUEST
         |BEGIN:VEVENT
         |DTSTART;VALUE=DATE:20240401
         |DTEND;VALUE=DATE:20240402
         |DTSTAMP:20240401T073652Z
         |ORGANIZER:mailto:hoangdat.pham2911@gmail.com
         |UID:7d8r01rdp8h2ivk2v1b4nlsnu9@google.com
         |ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
         | TRUE;CN=tddang@example.com;X-NUM-GUESTS=0:mailto:tddang@example.com
         |ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
         | TRUE;CN=dphamhoang@example.com;X-NUM-GUESTS=0:mailto:dphamhoang@linagora.c
         | om
         |ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=ACCEPTED;RSVP=TRUE
         | ;X-NUM-GUESTS=0:mailto:hoangdat.pham2911@gmail.com
         |ATTENDEE;CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT;PARTSTAT=NEEDS-ACTION;RSVP=
         | TRUE;CN=tdvu@example.com;X-NUM-GUESTS=0:mailto:tdvu@example.com
         |X-MICROSOFT-CDO-OWNERAPPTID:-1506694246
         |CREATED:20240401T073650Z
         |DESCRIPTION:&lt
         |LAST-MODIFIED:20240401T073650Z
         |LOCATION:
         |SEQUENCE:0
         |STATUS:CONFIRMED
         |SUMMARY:test
         |TRANSP:TRANSPARENT
         |END:VEVENT
         |END:VCALENDAR
         |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head
    assertThat(calendarEventParsed.participants.list.size)
      .isEqualTo(4)
  }

  @Nested
  class RecurrenceRuleTest {
    @Test
    def parseWeeklyShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("RRULE:FREQ=WEEKLY;BYDAY=MO,TU").getBytes())).head

      assertThat(calendarEventParsed.recurrenceRules.value.asJava)
        .hasSize(1)

      val recurrence: RecurrenceRule = calendarEventParsed.recurrenceRules.value.head
      assertThat(recurrence)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.WEEKLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.MO, WeekDay.Day.TU)))))
    }

    @Test
    def parseThirdSundayOfAprilShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("RRULE:FREQ=YEARLY;BYMONTH=4;BYDAY=SU;BYSETPOS=3").getBytes())).head

      assertThat(calendarEventParsed.recurrenceRules.value.asJava)
        .hasSize(1)

      val recurrence: RecurrenceRule = calendarEventParsed.recurrenceRules.value.head
      assertThat(recurrence)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.YEARLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.SU))),
          byMonth = Some(CalendarEventByMonth(Seq(new Month(4)))),
          bySetPosition = Some(Seq(3))))
    }

    @Test
    def parseFirstAndSecondMondayOfOctoberShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=MO;BYSETPOS=1,2").getBytes())).head

      assertThat(calendarEventParsed.recurrenceRules.value.asJava)
        .hasSize(1)

      val recurrence: RecurrenceRule = calendarEventParsed.recurrenceRules.value.head
      assertThat(recurrence)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.YEARLY),
          byMonth = Some(CalendarEventByMonth(Seq(new Month(10)))),
          bySetPosition = Some(List(1, 2)),
          byDay = Some(CalendarEventByDay(List(WeekDay.Day.MO)))))
    }

    @Test
    def parseMonthlyEvery29thOfEveryMonthShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("RRULE:FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=29").getBytes())).head

      assertThat(calendarEventParsed.recurrenceRules.value.asJava)
        .hasSize(1)

      val recurrence: RecurrenceRule = calendarEventParsed.recurrenceRules.value.head
      assertThat(recurrence)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.MONTHLY),
          interval = Some(RecurrenceRuleInterval.from(2)),
          byMonthDay = Some(List(29))))
    }

    @Test
    def parseMonthlyEveryLastSundayOfEvery3MonthsShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("RRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=SU;BYSETPOS=-1").getBytes())).head

      assertThat(calendarEventParsed.recurrenceRules.value.asJava)
        .hasSize(1)

      val recurrence: RecurrenceRule = calendarEventParsed.recurrenceRules.value.head
      assertThat(recurrence)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.MONTHLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.SU))),
          bySetPosition = Some(Seq(-1)),
          interval = Some(RecurrenceRuleInterval.from(3))))
    }

    @Test
    def parseMonthlyEveryFourthSundayOfEvery3MonthsShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("RRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=SU;BYSETPOS=4").getBytes())).head

      assertThat(calendarEventParsed.recurrenceRules.value.asJava)
        .hasSize(1)

      val recurrence: RecurrenceRule = calendarEventParsed.recurrenceRules.value.head
      assertThat(recurrence)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.MONTHLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.SU))),
          bySetPosition = Some(Seq(4)),
          interval = Some(RecurrenceRuleInterval.from(3))))
    }
  }

  @Nested
  class ExcludedRecurrenceRuleTest {
    @Test
    def parseWeeklyShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("EXRULE:FREQ=WEEKLY;BYDAY=MO,TU").getBytes())).head

      assertThat(calendarEventParsed.excludedRecurrenceRules.value.asJava)
        .hasSize(1)

      val excludedRecurrenceRule: RecurrenceRule = calendarEventParsed.excludedRecurrenceRules.value.head
      assertThat(excludedRecurrenceRule)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.WEEKLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.MO, WeekDay.Day.TU)))))
    }

    @Test
    def parseThirdSundayOfAprilShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("EXRULE:FREQ=YEARLY;BYMONTH=4;BYDAY=SU;BYSETPOS=3").getBytes())).head

      assertThat(calendarEventParsed.excludedRecurrenceRules.value.asJava)
        .hasSize(1)

      val excludedRecurrenceRule: RecurrenceRule = calendarEventParsed.excludedRecurrenceRules.value.head
      assertThat(excludedRecurrenceRule)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.YEARLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.SU))),
          byMonth = Some(CalendarEventByMonth(Seq(new Month(4)))),
          bySetPosition = Some(Seq(3))))
    }

    @Test
    def parseFirstAndSecondMondayOfOctoberShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("EXRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=MO;BYSETPOS=1,2").getBytes())).head

      assertThat(calendarEventParsed.excludedRecurrenceRules.value.asJava)
        .hasSize(1)

      val excludedRecurrenceRule: RecurrenceRule = calendarEventParsed.excludedRecurrenceRules.value.head
      assertThat(excludedRecurrenceRule)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.YEARLY),
          byMonth = Some(CalendarEventByMonth(Seq(new Month(10)))),
          bySetPosition = Some(List(1, 2)),
          byDay = Some(CalendarEventByDay(List(WeekDay.Day.MO)))))
    }

    @Test
    def parseMonthlyEvery29thOfEveryMonthShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("EXRULE:FREQ=MONTHLY;INTERVAL=2;BYMONTHDAY=29").getBytes())).head

      assertThat(calendarEventParsed.excludedRecurrenceRules.value.asJava)
        .hasSize(1)

      val excludedRecurrenceRule: RecurrenceRule = calendarEventParsed.excludedRecurrenceRules.value.head
      assertThat(excludedRecurrenceRule)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.MONTHLY),
          interval = Some(RecurrenceRuleInterval.from(2)),
          byMonthDay = Some(List(29))))
    }

    @Test
    def parseMonthlyEveryLastSundayOfEvery3MonthsShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("EXRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=SU;BYSETPOS=-1").getBytes())).head

      assertThat(calendarEventParsed.excludedRecurrenceRules.value.asJava)
        .hasSize(1)

      val excludedRecurrenceRule: RecurrenceRule = calendarEventParsed.excludedRecurrenceRules.value.head
      assertThat(excludedRecurrenceRule)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.MONTHLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.SU))),
          bySetPosition = Some(Seq(-1)),
          interval = Some(RecurrenceRuleInterval.from(3))))
    }

    @Test
    def parseMonthlyEveryFourthSundayOfEvery3MonthsShouldSucceed(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("EXRULE:FREQ=MONTHLY;INTERVAL=3;BYDAY=SU;BYSETPOS=4").getBytes())).head

      assertThat(calendarEventParsed.excludedRecurrenceRules.value.asJava)
        .hasSize(1)

      val excludedRecurrenceRule: RecurrenceRule = calendarEventParsed.excludedRecurrenceRules.value.head
      assertThat(excludedRecurrenceRule)
        .isEqualTo(RecurrenceRule(frequency = RecurrenceRuleFrequency(Frequency.MONTHLY),
          byDay = Some(CalendarEventByDay(Seq(WeekDay.Day.SU))),
          bySetPosition = Some(Seq(4)),
          interval = Some(RecurrenceRuleInterval.from(3))))
    }
  }

  @Nested
  class EventStatusTest {
    @Test
    def parseConfirmStatus(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("STATUS:CONFIRMED").getBytes())).head

      assertThat(calendarEventParsed.status.head)
        .isEqualTo(CalendarEventStatusField(Confirmed))
    }

    @Test
    def parseCancelStatus(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("STATUS:CANCELLED").getBytes())).head

      assertThat(calendarEventParsed.status.head)
        .isEqualTo(CalendarEventStatusField(Cancelled))
    }

    @Test
    def parseTentativeStatus(): Unit = {
      val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(
        new ByteArrayInputStream(icsPayloadWith("STATUS:TENTATIVE").getBytes())).head

      assertThat(calendarEventParsed.status.head)
        .isEqualTo(CalendarEventStatusField(Tentative))
    }
  }

  @Test
  def parseCounterEventShouldSucceed(): Unit = {
    val icsPayload =
      """BEGIN:VCALENDAR
        |VERSION:2.0
        |PRODID:-//Example Corp//NONSGML Event//EN
        |METHOD:COUNTER
        |BEGIN:VEVENT
        |UID:123456789@example.com
        |DTSTAMP:20250318T120000Z
        |DTSTART:20250320T150000Z
        |DTEND:20250320T160000Z
        |SUMMARY:Proposed New Time
        |ORGANIZER:mailto:organizer@example.com
        |ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:attendee@example.com
        |END:VEVENT
        |END:VCALENDAR
        |""".stripMargin

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes())).head

    SoftAssertions.assertSoftly(softly => {
      assertThat(calendarEventParsed.method.get.value)
        .isEqualTo("COUNTER")
      softly.assertThat(calendarEventParsed.start.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
        .isEqualTo("2025-03-20T15:00:00Z")
      assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
        .isEqualTo("2025-03-20T16:00:00Z")
    })
  }

  def icsPayloadWith(additionalField: String): String =
    s"""BEGIN:VCALENDAR
       |VERSION:2.0
       |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
       |CALSCALE:GREGORIAN
       |METHOD:REQUEST
       |BEGIN:VTIMEZONE
       |TZID:Asia/Ho_Chi_Minh
       |BEGIN:STANDARD
       |TZOFFSETFROM:+0700
       |TZOFFSETTO:+0700
       |TZNAME:ICT
       |DTSTART:19700101T000000
       |END:STANDARD
       |END:VTIMEZONE
       |BEGIN:VTIMEZONE
       |TZID:Asia/Ho_Chi_Minh
       |BEGIN:STANDARD
       |TZOFFSETFROM:+0700
       |TZOFFSETTO:+0700
       |TZNAME:ICT
       |DTSTART:19700101T000000
       |END:STANDARD
       |END:VTIMEZONE
       |BEGIN:VTIMEZONE
       |TZID:Asia/Ho_Chi_Minh
       |BEGIN:STANDARD
       |TZOFFSETFROM:+0700
       |TZOFFSETTO:+0700
       |TZNAME:ICT
       |DTSTART:19700101T000000
       |END:STANDARD
       |END:VTIMEZONE
       |BEGIN:VEVENT
       |UID:014351ba-ca86-4b0e-bf50-77d2f20afcb3
       |TRANSP:OPAQUE
       |DTSTART;TZID=Asia/Ho_Chi_Minh:20230328T103000
       |DTEND;TZID=Asia/Ho_Chi_Minh:20230328T113000
       |CLASS:PUBLIC
       |X-OPENPAAS-VIDEOCONFERENCE:
       |SUMMARY:Test
       |$additionalField
       |ORGANIZER;CN=Van Tung TRAN:mailto:vttran@domain.tld
       |DTSTAMP:20230328T030326Z
       |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL;CN=Van T
       | ung TRAN:mailto:vttran@domain.tld
       |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVI
       | DUAL:mailto:tungtv202@domain.tld
       |SEQUENCE:0
       |END:VEVENT
       |END:VCALENDAR
       |""".stripMargin

}
