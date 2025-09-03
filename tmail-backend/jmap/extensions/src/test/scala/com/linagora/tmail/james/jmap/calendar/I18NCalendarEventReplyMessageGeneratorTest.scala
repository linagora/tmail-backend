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
import java.util.Locale
import java.util.stream.Stream

import com.linagora.tmail.james.jmap.calendar.CalendarEventReplyGeneratorTest.calendarEventRequestTemplate
import com.linagora.tmail.james.jmap.calendar.I18NCalendarEventReplyMessageGeneratorTest.attendeeReply
import com.linagora.tmail.james.jmap.method.I18NCalendarEventReplyMessageGenerator
import com.linagora.tmail.james.jmap.model.{AttendeeReply, CalendarEventParsed}
import jakarta.mail.internet.{MimeMessage, MimeMultipart}
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.parameter.PartStat
import org.apache.james.core.MailAddress
import org.apache.james.server.core.filesystem.FileSystemImpl
import org.apache.james.util.MimeMessageUtil
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert.ThrowingCallable
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

object I18NCalendarEventReplyMessageGeneratorTest {
  val attendeeReply: AttendeeReply = AttendeeReply(new MailAddress("bob@domain.com"), PartStat.DECLINED)

  def i18nFileNameTestSuite: Stream[Arguments] = {
    Stream.of(
      Arguments.of(Locale.ENGLISH, PartStat.DECLINED, "calendar_reply_declined-en.eml"),
      Arguments.of(Locale.FRANCE, PartStat.ACCEPTED, "calendar_reply_accepted-fr.eml"),
      Arguments.of(Locale.ENGLISH, PartStat.TENTATIVE, "calendar_reply_tentative-en.eml"),
      Arguments.of(Locale.FRANCE, PartStat.DECLINED, "calendar_reply_declined-fr.eml"),
      Arguments.of(Locale.ENGLISH, PartStat.ACCEPTED, "calendar_reply_accepted-en.eml"))
  }
}

class I18NCalendarEventReplyMessageGeneratorTest {

  val testee: I18NCalendarEventReplyMessageGenerator = new I18NCalendarEventReplyMessageGenerator(FileSystemImpl.forTesting(), "classpath://eml/calendar_reply/")

  @Test
  def shouldDecoratedSubjectWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart.eml").block()

    assertThat(decoratedMessage.getSubject)
      .isEqualTo("Accepted: Simple event @ Fri Feb 23, 2024 (bob@domain.com)")
  }

  @Test
  def shouldDecoratedTextPlainBodyPartWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart.eml").block()

    assertThat(decoratedMessage.getContent)
      .isInstanceOf(classOf[MimeMultipart])
    assertThat(decoratedMessage.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getContent)
      .isEqualTo("bob@domain.com has accepted this invitation.\n")
  }

  @Test
  def shouldDecoratedTextHTMLBodyPartWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart_html.eml").block()

    assertThat(decoratedMessage.getContent)
      .isInstanceOf(classOf[MimeMultipart])
    assertThat(decoratedMessage.getContent.asInstanceOf[MimeMultipart].getBodyPart(0).getContent)
      .isEqualTo("""<!DOCTYPE html>
                   |<html lang="en">
                   |<head>
                   |    <meta charset="UTF-8">
                   |    <title>Example Email</title>
                   |</head>
                   |<body>
                   |    <h1>Hello, Taylor Swift <comptetest15.linagora@example.com>!</h1>
                   |    <p>This is an example HTML email.</p>
                   |    <p>For more information, visit <a href="https://example.com">example.com</a>.</p>
                   |</body>
                   |</html>
                   |""".stripMargin)
  }

  @Test
  def shouldDecoratedTextHTMLBodyPartWhenSinglepart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "singlepart_html.eml").block()

    assertThat(decoratedMessage.getContent)
      .isInstanceOf(classOf[String])
    assertThat(decoratedMessage.getContent.asInstanceOf[String])
      .isEqualTo("""<!DOCTYPE html>
                   |<html lang="en">
                   |<head>
                   |    <meta charset="UTF-8">
                   |    <title>Example HTML Email</title>
                   |</head>
                   |<body>
                   |    <h1>Hello, Taylor Swift <comptetest15.linagora@example.com>!</h1>
                   |    <p>This is an example HTML email.</p>
                   |    <p>For more information, visit <a href="https://example.com">example.com</a>.</p>
                   |</body>
                   |</html>
                   |""".stripMargin)
  }

  @Test
  def shouldDecoratedMessagesWhenSinglePart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "single.eml").block()

    assertThat(decoratedMessage.getSubject)
      .isEqualTo("Accepted: Simple event @ Fri Feb 23, 2024 (bob@domain.com)")
    assertThat(decoratedMessage.getContent.asInstanceOf[String])
      .isEqualTo("bob@domain.com has accepted this invitation.\n")
  }

  @ParameterizedTest
  @MethodSource(value = Array("i18nFileNameTestSuite"))
  def evaluatedMessageFileNameShouldSupportI18n(locale: Locale, partStat: PartStat, expectedFileName: String): Unit = {
    assertThat(testee.evaluateMailTemplateFileName(partStat, locale))
      .isEqualTo(expectedFileName)
  }

  @Test
  def shouldKeepAnotherPartWhenMultipart(): Unit = {
    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "multipart.eml").block()

    val originalMessage: MimeMessage = MimeMessageUtil.mimeMessageFromStream(ClassLoader.getSystemResourceAsStream("eml/calendar_reply/multipart.eml"))

    assertThat(decoratedMessage.getContent.asInstanceOf[MimeMultipart].getCount)
      .isEqualTo(originalMessage.getContent.asInstanceOf[MimeMultipart].getCount)
  }

  @Test
  def getBasedMimeMessageShouldThrowWhenTemplateNotFound(): Unit = {
    val throwingCallable: ThrowingCallable = () => testee.getBasedMimeMessage(attendeeReply, calendarEventRequestTemplate, "unknown.eml").block()
    assertThatThrownBy(throwingCallable)
      .isInstanceOf(classOf[IllegalArgumentException])
    assertThatThrownBy(throwingCallable)
      .hasMessageContaining("Cannot find the template eml file: classpath://eml/calendar_reply/unknown.eml")
  }

  @Test
  def shouldDecoratedMustacheData(): Unit = {
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
         |ORGANIZER;CN=John Nathan;CN=comptetest15.linagora@domain.tld:mailto:comptetest15.linagora@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:btellier@example.com
         |ATTENDEE;PARTSTAT=NEEDS-ACTION;RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL:mailto:other@example.com
         |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CN=Bob TRAN;CUTYPE=INDIVIDUAL:mailto:bob@domain.com
         |DTSTAMP:20240222T204008Z
         |SEQUENCE:0
         |END:VEVENT
         |END:VCALENDAR""".stripMargin

    val calendarRequest: Calendar = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(requestDoesNotContainCalScale.getBytes()))

    val decoratedMessage: MimeMessage = testee.getBasedMimeMessage(attendeeReply, calendarRequest, "mustache_show.eml").block()

    assertThat(decoratedMessage.getContent.asInstanceOf[String].replaceAll("\\n|\\r\\n", System.getProperty("line.separator")))
      .isEqualTo(
        s"""ATTENDEE: Bob TRAN <bob@domain.com>
           |ATTENDEE_CN: Bob TRAN
           |PART_STAT: DECLINED
           |ORGANIZER: John Nathan <comptetest15.linagora@example.com>
           |ORGANIZER_CN: John Nathan
           |EVENT_TITLE: Simple event
           |EVENT_START_DATE: Fri Feb 23, 2024
           |EVENT_END_DATE: Fri Feb 23, 2024
           |EVENT_LOCATION: """.stripMargin)
  }
}
