package com.linagora.tmail.james.jmap.calendar

import java.io.ByteArrayInputStream
import java.time.format.DateTimeFormatter

import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes()))

    assertThat(calendarEventParsed.end.get.value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
      .isEqualTo("2017-01-21T10:00:00Z")
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

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes()))

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

    val calendarEventParsed: CalendarEventParsed = CalendarEventParsed.from(new ByteArrayInputStream(icsPayload.getBytes()))

    assertThat(calendarEventParsed.method.get.value).isEqualTo("REQUEST")
    assertThat(calendarEventParsed.sequence.get.value).isEqualTo(0)
    assertThat(calendarEventParsed.uid.get.value).isEqualTo("ea127690-0440-404b-af98-9823c855a283")
    assertThat(calendarEventParsed.priority.get.value).isEqualTo(5)
    assertThat(s"${calendarEventParsed.freeBusyStatus.get.value}").isEqualTo("busy")
    assertThat(s"${calendarEventParsed.privacy.get.value}").isEqualTo("public")
  }
}
