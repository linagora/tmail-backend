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
import java.time.temporal.Temporal
import java.util.stream.Stream

import com.linagora.tmail.james.jmap.model.{AttendeeReply, CalendarEventParsed, CalendarEventReplyGenerator => testee}
import net.fortuna.ical4j.model.component.{VEvent, VTimeZone}
import net.fortuna.ical4j.model.parameter.{Cn, PartStat}
import net.fortuna.ical4j.model.property.Attendee
import net.fortuna.ical4j.model.{Calendar, Component}
import org.apache.james.core.MailAddress
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

object CalendarEventReplyGeneratorTest {
  val partStats: Stream[Arguments] = {
    Stream.of(
      Arguments.of(PartStat.ACCEPTED),
      Arguments.of(PartStat.DECLINED),
      Arguments.of(PartStat.TENTATIVE))
  }

  val invitationDtStamp: String = "20240222T204008Z"
  val invitationLastModified: String = "20240222T204008Z"

  val calendarEventRequestTemplate: Calendar = {
    val payload: String =
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
         |CALSCALE:GREGORIAN
         |METHOD:REQUEST
         |BEGIN:VTIMEZONE
         |TZID:Europe/Paris
         |BEGIN:DAYLIGHT
         |TZOFFSETFROM:+0100
         |TZOFFSETTO:+0200
         |TZNAME:CEST
         |DTSTART:19700329T020000
         |RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
         |END:DAYLIGHT
         |BEGIN:STANDARD
         |TZOFFSETFROM:+0200
         |TZOFFSETTO:+0100
         |TZNAME:CET
         |DTSTART:19701025T030000
         |RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
         |END:STANDARD
         |END:VTIMEZONE
         |BEGIN:VEVENT
         |UID:8eae5147-f2df-4853-8fe0-c88678bc8b9f
         |TRANSP:OPAQUE
         |DTSTART;TZID=Europe/Paris:20240223T160000
         |DTEND;TZID=Europe/Paris:20240223T163000
         |CLASS:PUBLIC
         |SUMMARY:Simple event
         |ORGANIZER;CN=Taylor Swift:mailto:comptetest15.linagora@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:btellier@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:other@example.com
         |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:bob@domain.com
         |DTSTAMP:$invitationDtStamp
         |LAST-MODIFIED:$invitationLastModified
         |SEQUENCE:0
         |END:VEVENT
         |END:VCALENDAR""".stripMargin

    CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(payload.getBytes()))
  }
}

class CalendarEventReplyGeneratorTest {
  import CalendarEventReplyGeneratorTest._

  @Test
  def shouldRemoveAllUnrelatedParticipantsInfo(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    val replyAttendee: String = getVEVENT(replyCalendarEvent).getProperties("ATTENDEE").toString

    assertSoftly(softly => {
      softly.assertThat(replyAttendee).doesNotContain("other@example.com")
      softly.assertThat(replyAttendee).doesNotContain("btellier@example.com")
    })
  }

  @Test
  def shouldOverrideDtStampAndLastModified(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertSoftly(softly => {
      softly.assertThat(getVEVENT(replyCalendarEvent)
        .getDateTimeStart[Temporal]().getValue)
        .isNotEqualTo(invitationDtStamp)

      softly.assertThat(getVEVENT(replyCalendarEvent)
        .getLastModified
        .getValue)
        .isNotEqualTo(invitationLastModified)
    })
  }

  @Test
  def shouldCreateNewDtStampAndLastModifiedForEveryReply(): Unit = {
    val replyCalendarEvent1: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.ACCEPTED))
    Thread.sleep(1000L) // avoid having the same second (second is the best precision following iCalendar (RFC 5545) BTW)
    val replyCalendarEvent2: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertSoftly(softly => {
      softly.assertThat(getVEVENT(replyCalendarEvent1)
          .getDateTimeStamp.getValue)
        .isNotEqualTo(getVEVENT(replyCalendarEvent2)
          .getDateTimeStamp.getValue)

      softly.assertThat(getVEVENT(replyCalendarEvent1)
        .getLastModified
        .getValue)
        .isNotEqualTo(getVEVENT(replyCalendarEvent2)
          .getLastModified
          .getValue)
    })
  }

  @Test
  def shouldCreateNewDtStampAndLastModifiedIfNotPresentInTheInviteIcs(): Unit = {
    val invitationDoesNotContainDtStampAndLastModified: String =
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
         |CALSCALE:GREGORIAN
         |METHOD:REQUEST
         |BEGIN:VTIMEZONE
         |TZID:Europe/Paris
         |BEGIN:DAYLIGHT
         |TZOFFSETFROM:+0100
         |TZOFFSETTO:+0200
         |TZNAME:CEST
         |DTSTART:19700329T020000
         |RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
         |END:DAYLIGHT
         |BEGIN:STANDARD
         |TZOFFSETFROM:+0200
         |TZOFFSETTO:+0100
         |TZNAME:CET
         |DTSTART:19701025T030000
         |RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
         |END:STANDARD
         |END:VTIMEZONE
         |BEGIN:VEVENT
         |UID:8eae5147-f2df-4853-8fe0-c88678bc8b9f
         |TRANSP:OPAQUE
         |DTSTART;TZID=Europe/Paris:20240223T160000
         |DTEND;TZID=Europe/Paris:20240223T163000
         |CLASS:PUBLIC
         |SUMMARY:Simple event
         |ORGANIZER;CN=Taylor Swift:mailto:comptetest15.linagora@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:btellier@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:other@example.com
         |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:bob@domain.com
         |SEQUENCE:0
         |END:VEVENT
         |END:VCALENDAR""".stripMargin
    val invitation: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(invitationDoesNotContainDtStampAndLastModified.getBytes()))

    val replyCalendarEvent: Calendar = testee.generate(invitation, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertSoftly(softly => {
      softly.assertThat(getVEVENT(replyCalendarEvent)
          .getDateTimeStart[Temporal]().getValue)
        .isNotNull

      softly.assertThat(getVEVENT(replyCalendarEvent)
        .getLastModified
        .getValue)
        .isNotNull
    })
  }

  @Test
  def shouldAddTheAttendeeToTheReply(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))
    val replyAttendee: String = getVEVENT(replyCalendarEvent).getProperty("ATTENDEE").toString
    assertSoftly(softly => {
      softly.assertThat(replyAttendee).contains("ROLE=REQ-PARTICIPANT")
      softly.assertThat(replyAttendee).contains("PARTSTAT=DECLINED")
      softly.assertThat(replyAttendee).contains("CUTYPE=INDIVIDUAL:mailto:bob@domain.com")
    })
  }

  @Test
  def shouldAddTheAttendeeToTheReplyEvenWhenWasNotInvited(): Unit = {
    val emailWasNotInvited = "not-invited@domain.com";
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress(emailWasNotInvited), PartStat.DECLINED))
    val replyAttendee: Option[Attendee] = getVEVENT(replyCalendarEvent).getProperty[Attendee]("ATTENDEE").toScala
    assertThat(replyAttendee.isDefined).isTrue
    assertThat(replyAttendee.get.getCalAddress.toString).isEqualTo("mailto:" + emailWasNotInvited)
  }

  @Test
  def shouldKeepCalScaleFromRequest(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertThat(replyCalendarEvent.getCalendarScale.getValue)
      .isEqualTo(calendarEventRequestTemplate.getCalendarScale.getValue)
  }

  @Test
  def shouldHasMethodReply(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))
    assertThat(replyCalendarEvent.getMethod.getValue)
      .isEqualTo("REPLY")
  }

  @ParameterizedTest
  @MethodSource(value = Array("partStats"))
  def shouldGenerateCorrectPartStat(partStat: PartStat): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), partStat))
    val replyAttendee: Attendee = getVEVENT(replyCalendarEvent).getProperty[Attendee]("ATTENDEE").get()
    assertThat(replyAttendee.getParameter[PartStat]("PARTSTAT").get.getValue)
      .isEqualTo(partStat.getValue)
  }

  @Test
  def shouldKeepAlmostVEventPropertiesOfRequest(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertThat(getVEVENT(replyCalendarEvent).toString.replaceAll("\\n|\\r\\n", System.lineSeparator()).trim)
      .matches(
        """BEGIN:VEVENT
          |UID:8eae5147-f2df-4853-8fe0-c88678bc8b9f
          |TRANSP:OPAQUE
          |DTSTART;TZID=Europe/Paris:20240223T160000
          |DTEND;TZID=Europe/Paris:20240223T163000
          |CLASS:PUBLIC
          |SUMMARY:Simple event
          |ORGANIZER;CN=Taylor Swift:mailto:comptetest15.linagora@example.com
          |SEQUENCE:0
          |ATTENDEE;ROLE=REQ-PARTICIPANT;PARTSTAT=DECLINED;CUTYPE=INDIVIDUAL:mailto:bob@domain.com
          |DTSTAMP:\d{8}T\d{6}Z
          |LAST-MODIFIED:\d{8}T\d{6}Z
          |END:VEVENT
          |""".stripMargin.trim)
  }

  @Test
  def shouldThrowWhenMethodIsNotRequest(): Unit = {
    val replyCalendar: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertThatThrownBy(() => testee.generate(replyCalendar, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED)))
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def shouldNotThrownWhenCalScaleAbsentInRequest(): Unit = {

    val requestDoesNotContainCalScale: String =
      s"""BEGIN:VCALENDAR
         |VERSION:2.0
         |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
         |METHOD:REQUEST
         |BEGIN:VTIMEZONE
         |TZID:Europe/Paris
         |BEGIN:DAYLIGHT
         |TZOFFSETFROM:+0100
         |TZOFFSETTO:+0200
         |TZNAME:CEST
         |DTSTART:19700329T020000
         |RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
         |END:DAYLIGHT
         |BEGIN:STANDARD
         |TZOFFSETFROM:+0200
         |TZOFFSETTO:+0100
         |TZNAME:CET
         |DTSTART:19701025T030000
         |RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
         |END:STANDARD
         |END:VTIMEZONE
         |BEGIN:VEVENT
         |UID:8eae5147-f2df-4853-8fe0-c88678bc8b9f
         |TRANSP:OPAQUE
         |DTSTART;TZID=Europe/Paris:20240223T160000
         |DTEND;TZID=Europe/Paris:20240223T163000
         |CLASS:PUBLIC
         |SUMMARY:Simple event
         |ORGANIZER;CN=comptetest15.linagora@domain.tld:mailto:comptetest15.linagora@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:btellier@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:other@example.com
         |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:bob@domain.com
         |DTSTAMP:20240222T204008Z
         |SEQUENCE:0
         |END:VEVENT
         |END:VCALENDAR""".stripMargin

    val calendarRequest: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(requestDoesNotContainCalScale.getBytes()))

    assertThatCode(() => testee.generate(calendarRequest, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED)))
      .doesNotThrowAnyException()
  }

  @Test
  def shouldShowAttendeeCNWhenPresent(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED, Some(new Cn("Tran Van Tung"))))

    val replyAttendee: String = getVEVENT(replyCalendarEvent).getProperties("ATTENDEE").toString

    assertThat(replyAttendee).contains("CN=Tran Van Tung")
  }

  @Test
  def shouldCopyVTimezoneFromRequest(): Unit = {
    val replyCalendarEvent: Calendar = testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED))

    assertThat(replyCalendarEvent.getComponent[VTimeZone]("VTIMEZONE"))
      .isEqualTo(calendarEventRequestTemplate.getComponent[VTimeZone]("VTIMEZONE"))
  }

  @Test
  def shouldNotThrowWhenNotInvitedToAttend(): Unit = {
    assertThatCode(() => testee.generate(calendarEventRequestTemplate, AttendeeReply(new MailAddress("not-invited@domain.com"), PartStat.DECLINED)))
      .doesNotThrowAnyException()
  }

  private def getVEVENT(calendar: Calendar): VEvent = calendar.getComponents[VEvent](Component.VEVENT).asScala.head
}
