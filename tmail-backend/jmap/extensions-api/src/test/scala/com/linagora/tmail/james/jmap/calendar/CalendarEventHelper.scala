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
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZonedDateTime}
import java.util.UUID

import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import net.fortuna.ical4j.model.Calendar
import net.fortuna.ical4j.model.parameter.PartStat

object CalendarEventHelper {
  private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}

case class CalendarEventHelper(uid: String,
                               start: ZonedDateTime,
                               end: ZonedDateTime,
                               attendee: String,
                               partStat: PartStat = PartStat.NEEDS_ACTION,
                               organizer: Option[String] = None) {
  import CalendarEventHelper._

  def this(attendee: String, partStat: PartStat, start: ZonedDateTime, end: ZonedDateTime) =
    this(UUID.randomUUID().toString, start, end, attendee, partStat, None)

  def asCalendar: Calendar =
    CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(asText.getBytes(StandardCharsets.UTF_8)))

  def asText: String =
    s"""BEGIN:VCALENDAR
       |VERSION:2.0
       |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
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
       |UID:$uid
       |TRANSP:OPAQUE
       |DTSTART;TZID=Asia/Jakarta:${start.withZoneSameInstant(ZoneId.of("Asia/Jakarta")).format(formatter)}
       |DTEND;TZID=Asia/Jakarta:${end.withZoneSameInstant(ZoneId.of("Asia/Jakarta")).format(formatter)}
       |CLASS:PUBLIC
       |SUMMARY:Test1
       |DESCRIPTION:Note1
       |LOCATION:Location2
       |ORGANIZER;CN=John1 Doe1:mailto:${organizer.getOrElse("user2@open-paas.org")}
       |ATTENDEE;PARTSTAT=${partStat.getValue};RSVP=TRUE;ROLE=REQ-PARTICIPANT;CUTYPE=INDIVIDUAL;CN=John2 Doe2;SCHEDULE-STATUS=1.2:mailto:$attendee
       |ATTENDEE;PARTSTAT=ACCEPTED;RSVP=FALSE;ROLE=CHAIR;CUTYPE=INDIVIDUAL:mailto:${organizer.getOrElse("user2@open-paas.org")}
       |DTSTAMP:${ZonedDateTime.now().format(formatter)}
       |END:VEVENT
       |END:VCALENDAR""".stripMargin

  def asByte: Array[Byte] = asText.getBytes(StandardCharsets.UTF_8)

  def generateCounterEvent(proposeStartDate: ZonedDateTime, proposeEndDate: ZonedDateTime): Calendar =
    CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
      s"""BEGIN:VCALENDAR
        |VERSION:2.0
        |PRODID:-//Sabre//Sabre VObject 4.2.2//EN
        |METHOD:COUNTER
        |BEGIN:VEVENT
        |UID:$uid
        |DTSTAMP:20250318T120000Z
        |DTSTART;TZID=Asia/Jakarta:${proposeStartDate.withZoneSameInstant(ZoneId.of("Asia/Jakarta")).format(formatter)}
        |DTEND;TZID=Asia/Jakarta:${proposeEndDate.withZoneSameInstant(ZoneId.of("Asia/Jakarta")).format(formatter)}
        |SUMMARY:Proposed New Time
        |ORGANIZER:mailto:${organizer.getOrElse("user2@open-paas.org")}
        |ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:$attendee
        |END:VEVENT
        |END:VCALENDAR
        |""".stripMargin.getBytes(StandardCharsets.UTF_8)))
}