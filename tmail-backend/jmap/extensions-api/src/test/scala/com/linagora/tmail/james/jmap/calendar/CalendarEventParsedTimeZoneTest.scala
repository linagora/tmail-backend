/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero General Public License       *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 ********************************************************************/

package com.linagora.tmail.james.jmap.calendar

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.{Instant, ZonedDateTime}

import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.{Calendar, Component}
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{CsvSource, ValueSource}

import scala.jdk.CollectionConverters._

class CalendarEventParsedTimeZoneTest {
  private val ics = """BEGIN:VCALENDAR
                      |VERSION:2.0
                      |PRODID:-//Twake Mail//Timezone test//EN
                      |BEGIN:VTIMEZONE
                      |TZID:{TZID}
                      |BEGIN:DAYLIGHT
                      |DTSTART:19700329T020000
                      |TZOFFSETFROM:+0100
                      |TZOFFSETTO:+0200
                      |RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
                      |END:DAYLIGHT
                      |BEGIN:STANDARD
                      |DTSTART:19701025T030000
                      |TZOFFSETFROM:+0200
                      |TZOFFSETTO:+0100
                      |RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
                      |END:STANDARD
                      |END:VTIMEZONE
                      |BEGIN:VEVENT
                      |UID:timezone-test
                      |DTSTAMP:20261026T000000Z
                      |DTSTART;TZID={TZID}:{DATE}T114500
                      |DTEND;TZID={TZID}:{DATE}T123000
                      |END:VEVENT
                      |END:VCALENDAR
                      |""".stripMargin

  @ParameterizedTest
  @ValueSource(strings = Array("Europe/Paris", "Romance Standard Time"))
  def shouldNotExhaustTimezonePoolWhileRetainingParsedCalendars(tzId: String): Unit = {
    val calendars = (1 to 2000).map(_ => parse(ics.replace("{TZID}", tzId).replace("{DATE}", "20261026")))

    assertThat(calendars.asJava).hasSize(2000)
    calendars.foreach(calendar => assertThat(start(calendar)).isEqualTo(Instant.parse("2026-10-26T10:45:00Z")))
  }

  @ParameterizedTest
  @CsvSource(Array("20260715, 2026-07-15T09:45:00Z", "20261026, 2026-10-26T10:45:00Z"))
  def shouldResolveSummerAndWinterOffsets(date: String, expected: String): Unit = {
    val calendar = parse(ics.replace("{TZID}", "Europe/Paris").replace("{DATE}", date))

    assertThat(start(calendar)).isEqualTo(Instant.parse(expected))
  }

  @Test
  def shouldResolveCustomTimezoneFromItsDefinition(): Unit = {
    val calendar = parse(ics.replace("{TZID}", "Custom Twake Time").replace("{DATE}", "20260715"))

    assertThat(start(calendar)).isEqualTo(Instant.parse("2026-07-15T09:45:00Z"))
  }

  private def parse(content: String): Calendar =
    CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))

  private def start(calendar: Calendar): Instant =
    calendar.getComponent[VEvent](Component.VEVENT).orElseThrow().getDateTimeStart.getDate.asInstanceOf[ZonedDateTime].toInstant
}
