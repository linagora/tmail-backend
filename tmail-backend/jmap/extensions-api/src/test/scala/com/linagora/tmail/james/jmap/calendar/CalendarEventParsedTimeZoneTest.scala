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

import java.io.{ByteArrayInputStream, StringReader}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.{Instant, ZonedDateTime}

import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import net.fortuna.ical4j.data.{CalendarBuilder, CalendarParserFactory, ParserException}
import net.fortuna.ical4j.model.component.{VEvent, VTimeZone}
import net.fortuna.ical4j.model.property.Attach
import net.fortuna.ical4j.model.{Calendar, Component, Property, TimeZone, TimeZoneRegistry}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
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

  @Test
  def shouldRejectACalendarWithTooManyCustomTimezones(): Unit = {
    // Without recurrence rules, the recurrence budget of the calendar does not stop the registrations
    val timezones = (0 until 1600).map(i => fixedOffsetTimezone(s"X-$i")).mkString

    assertThatThrownBy(() => parse(calendarWith(timezones, "X-0"))).isInstanceOf(classOf[ParserException])
    assertThat(start(parse(ics.replace("{TZID}", "Custom Twake Time").replace("{DATE}", "20260715"))))
      .isEqualTo(Instant.parse("2026-07-15T09:45:00Z"))
  }

  @Test
  def shouldAcceptTheMaximumOfCustomTimezones(): Unit = {
    val timezones = (0 until ICal4JTimeZoneRegistry.MAX_CUSTOM_TIMEZONES).map(i => timezone(s"X-$i")).mkString

    assertThat(start(parse(calendarWith(timezones, "X-1")))).isEqualTo(Instant.parse("2026-07-15T09:45:00Z"))
  }

  @Test
  def shouldRegisterARepeatedCustomTimezoneOnce(): Unit = {
    val timezones = timezone("Custom Twake Time") * 1600
    val calendars = (1 to 2).map(_ => parse(calendarWith(timezones, "Custom Twake Time")))

    calendars.foreach(calendar => assertThat(start(calendar)).isEqualTo(Instant.parse("2026-07-15T09:45:00Z")))
  }

  // ical4j expands the rules of several STANDARD observances when building the timezone, before registering it: a
  // standard TZID, whose definition the registry then ignores, is expanded as well
  @ParameterizedTest
  @ValueSource(strings = Array("Europe/Paris", "Custom Twake Time"))
  def shouldRejectATimezoneWhoseObservancesAreTooCostlyToBuild(tzId: String): Unit = {
    val costly = standardObservance("19700101T000000", "FREQ=SECONDLY")
    val timezones = s"BEGIN:VTIMEZONE\nTZID:$tzId\n$costly${standardObservance("19701025T030000", "FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU")}END:VTIMEZONE\n"

    assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () =>
      assertThatThrownBy(() => parse(calendarWith(timezones, tzId))).isInstanceOf(classOf[ParserException]))
  }

  // Each frequency period is walked, even when few of them hold an occurrence
  @Test
  def shouldRejectATimezoneWalkingTooManyPeriods(): Unit = {
    val timezones = s"BEGIN:VTIMEZONE\nTZID:Sparse\n${standardObservance("19700101T000000", "FREQ=SECONDLY;BYMINUTE=0,15,30,45;BYSECOND=0")}END:VTIMEZONE\n"

    assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () =>
      assertThatThrownBy(() => parse(calendarWith(timezones, "Sparse"))).isInstanceOf(classOf[ParserException]))
  }

  @Test
  def shouldAcceptAnnualRulesStartingIn1601(): Unit = {
    val timezones = timezone("Custom Twake Time").replace("19700329", "16010325").replace("19701025", "16011028")

    assertThat(start(parse(calendarWith(timezones, "Custom Twake Time")))).isEqualTo(Instant.parse("2026-07-15T09:45:00Z"))
  }

  @Test
  def aRejectedTimezoneShouldNotConsumeTheCustomTimezoneBudget(): Unit = {
    val budget = ICal4JTimeZoneRegistry.newCustomTimezoneBudget()
    val registry = new ICal4JTimeZoneRegistry(budget)
    val tooCostly = new TimeZone(new CalendarBuilder(CalendarParserFactory.getInstance().get(), null: TimeZoneRegistry)
      .build(new StringReader(s"BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:test\nBEGIN:VTIMEZONE\nTZID:Costly\n${standardObservance("19700101T000000", "FREQ=SECONDLY")}END:VTIMEZONE\nEND:VCALENDAR\n"))
      .getComponent[VTimeZone](Component.VTIMEZONE).orElseThrow())

    (0 until 2).foreach(_ => assertThatThrownBy(() => registry.register(tooCostly)).isInstanceOf(classOf[IllegalArgumentException]))

    assertThat(budget.get()).isEqualTo(ICal4JTimeZoneRegistry.MAX_CUSTOM_TIMEZONES)
  }

  @Test
  def parsingAndCopyingAnInlineAttachmentShouldLeaveNoTemporaryFile(): Unit = {
    val content = """BEGIN:VCALENDAR
                    |VERSION:2.0
                    |PRODID:x
                    |BEGIN:VEVENT
                    |UID:a
                    |DTSTAMP:20260401T100000Z
                    |DTSTART:20260401T100000Z
                    |ATTACH;ENCODING=BASE64;VALUE=BINARY;FMTTYPE=text/plain:aGVsbG8=
                    |ATTACH:https://example.com/a.pdf
                    |END:VEVENT
                    |END:VCALENDAR
                    |""".stripMargin
    val before = ical4jAttachFiles()

    val calendar = parse(content)
    val copy = CalendarEventModifier.deepCopy(calendar)

    assertThat(ical4jAttachFiles().asJava).isEqualTo(before.asJava)
    val attachments = copy.getComponent[VEvent](Component.VEVENT).orElseThrow().getProperties[Attach](Property.ATTACH).asScala
    assertThat(new String(attachments.head.getBinaryData, StandardCharsets.US_ASCII)).isEqualTo("hello")
    assertThat(attachments(1).getUri.toString).isEqualTo("https://example.com/a.pdf")
    assertThat(copy.toString).isEqualTo(calendar.toString)
  }

  private def ical4jAttachFiles(): List[Path] = {
    val files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))
    try {
      files.iterator().asScala.filter(_.getFileName.toString.startsWith("ical4j-attach")).toList.sortBy(_.toString)
    } finally {
      files.close()
    }
  }

  private def timezone(tzId: String): String =
    ics.substring(ics.indexOf("BEGIN:VTIMEZONE"), ics.indexOf("BEGIN:VEVENT")).replace("{TZID}", tzId)

  private def standardObservance(dtStart: String, rrule: String): String =
    s"BEGIN:STANDARD\nDTSTART:$dtStart\nTZOFFSETFROM:+0200\nTZOFFSETTO:+0100\nRRULE:$rrule\nEND:STANDARD\n"

  private def fixedOffsetTimezone(tzId: String): String =
    s"""BEGIN:VTIMEZONE
       |TZID:$tzId
       |BEGIN:STANDARD
       |DTSTART:19700101T000000
       |TZOFFSETFROM:+0100
       |TZOFFSETTO:+0100
       |END:STANDARD
       |END:VTIMEZONE
       |""".stripMargin

  private def calendarWith(timezones: String, usedTzId: String): String = {
    val event = ics.substring(ics.indexOf("BEGIN:VEVENT")).replace("{TZID}", usedTzId).replace("{DATE}", "20260715")
    ics.substring(0, ics.indexOf("BEGIN:VTIMEZONE")) + timezones + event
  }

  private def parse(content: String): Calendar =
    CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))

  private def start(calendar: Calendar): Instant =
    calendar.getComponent[VEvent](Component.VEVENT).orElseThrow().getDateTimeStart.getDate.asInstanceOf[ZonedDateTime].toInstant
}
