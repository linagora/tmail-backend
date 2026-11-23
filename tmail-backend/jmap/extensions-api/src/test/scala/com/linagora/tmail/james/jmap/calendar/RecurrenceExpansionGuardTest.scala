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
import java.time.Instant
import java.time.temporal.Temporal
import java.util.stream.Stream

import com.linagora.tmail.james.jmap.model.CalendarEventParsed
import net.fortuna.ical4j.model.component.VEvent
import net.fortuna.ical4j.model.parameter.PartStat
import net.fortuna.ical4j.model.property.RecurrenceId
import net.fortuna.ical4j.model.{Component, Property, Recur}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.ThrowableAssert
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.{Arguments, MethodSource}

object RecurrenceExpansionGuardTest {
  private def range(from: Int, to: Int): String = (from to to).mkString(",")

  private val FULL_LISTS = s"BYMONTH=${range(1, 12)};BYMONTHDAY=${range(1, 31)};BYHOUR=${range(0, 23)};BYMINUTE=${range(0, 59)};BYSECOND=${range(0, 59)}"
  private val REPEATED_LISTS = "BYHOUR=" + "0," * 1000 + "0;BYMINUTE=" + "0," * 1000 + "0;BYSECOND=" + "0," * 1000 + "0"

  def rulesTooCostlyToExpand(): Stream[Arguments] = Stream.of(
    Arguments.of("COUNT walked from DTSTART", "DTSTART:19900101T000000Z\nRRULE:FREQ=SECONDLY;COUNT=2000000000"),
    Arguments.of("int overflow of the fast-forward", "DTSTART:19000101T000000Z\nRRULE:FREQ=SECONDLY"),
    Arguments.of("int overflow of the fast-forward with a power of two INTERVAL", "DTSTART:19000101T000000Z\nRRULE:FREQ=SECONDLY;INTERVAL=1024"),
    Arguments.of("DURATION widening the walk", "DTSTART:20000101T000000Z\nRRULE:FREQ=SECONDLY\nDURATION:P1400W"),
    Arguments.of("DTEND widening the walk", "DTSTART:20000101T000000Z\nRRULE:FREQ=SECONDLY\nDTEND:20260101T000000Z"),
    Arguments.of("BY* lists product", s"DTSTART:20260101T000000Z\nRRULE:FREQ=YEARLY;$FULL_LISTS"),
    Arguments.of("repeated BY* values", s"DTSTART:20260101T000000Z\nRRULE:FREQ=YEARLY;$REPEATED_LISTS"),
    Arguments.of("BY* lists product of an EXRULE", s"DTSTART:20260101T000000Z\nRRULE:FREQ=DAILY\nEXRULE:FREQ=YEARLY;$FULL_LISTS"),
    Arguments.of("BYDAY of a YEARLY rule", s"DTSTART:20260101T000000Z\nRRULE:FREQ=YEARLY;BYDAY=MO,TU,WE,TH,FR,SA,SU;BYHOUR=${range(0, 23)};BYMINUTE=${range(0, 59)};BYSECOND=${range(0, 59)}"),
    Arguments.of("BYMONTHDAY of a YEARLY rule without BYMONTH", s"DTSTART:20260101T000000Z\nRRULE:FREQ=YEARLY;BYMONTHDAY=${range(1, 31)};BYHOUR=${range(0, 23)};BYMINUTE=${range(0, 59)};BYSECOND=${range(0, 19)}"))

  def sparseCountedRules(): Stream[String] = Stream.of(
    "FREQ=MINUTELY;COUNT=1000;BYHOUR=0;BYSECOND=0,1,2,3,4,5,6,7,8,9",
    "FREQ=HOURLY;COUNT=10;BYDAY=MO;BYMINUTE=" + range(0, 29))

  def realWorldRules(): Stream[String] = Stream.of(
    "FREQ=WEEKLY;COUNT=81920;BYDAY=MO,TU,WE,TH,FR",
    "FREQ=DAILY;COUNT=5000;BYDAY=MO,TU,WE,TH,FR",
    "FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU",
    "FREQ=MONTHLY;BYMONTHDAY=15;COUNT=24")
}

class RecurrenceExpansionGuardTest {
  private def calendar(rule: String) = CalendarEventParsed.parseICal4jCalendar(new ByteArrayInputStream(
    s"""BEGIN:VCALENDAR
       |VERSION:2.0
       |PRODID:-//Twake Mail//Recurrence test//EN
       |BEGIN:VEVENT
       |UID:recurring-uid@test
       |DTSTAMP:20250318T120000Z
       |$rule
       |ORGANIZER:mailto:organizer@example.com
       |ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:user2@open-paas.org
       |END:VEVENT
       |END:VCALENDAR
       |""".stripMargin.getBytes(StandardCharsets.UTF_8)))

  private def acceptOccurrence(rule: String, recurrenceId: String) =
    CalendarEventModifier(Seq(AttendeePartStatusUpdatePatch("user2@open-paas.org", PartStat.ACCEPTED)),
      Some(new RecurrenceId[Temporal](recurrenceId)))
      .apply(calendar(rule))

  @ParameterizedTest(name = "{0}")
  @MethodSource(Array("rulesTooCostlyToExpand"))
  def acceptingAnOccurrenceShouldRefuseARuleTooCostlyToExpand(description: String, rule: String): Unit =
    assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () =>
      assertThatThrownBy(() => acceptOccurrence(rule, "20260408T100000Z")).asInstanceOf[ThrowableAssert[Throwable]]
        .isInstanceOf(classOf[IllegalArgumentException]))

  @Test
  def acceptingAnOccurrenceShouldStillExpandALongCountedSeries(): Unit = {
    val updated = assertTimeoutPreemptively(java.time.Duration.ofSeconds(5), () =>
      acceptOccurrence("DTSTART:20200101T100000Z\nDURATION:PT1H\nRRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;COUNT=5000", "20260408T100000Z"))

    val occurrence = updated.getComponents[VEvent](Component.VEVENT).stream()
      .filter(vEvent => vEvent.getProperty(Property.RECURRENCE_ID).isPresent)
      .findFirst().orElseThrow()
    assertThat(occurrence.getDateTimeStart.getValue).startsWith("20260408T100000")
  }

  @ParameterizedTest
  @MethodSource(Array("sparseCountedRules"))
  def canExpandShouldNotBoundTheWalkByCountWhenPeriodsMayBeEmpty(rule: String): Unit =
    assertThat(RecurrenceExpansionGuard.canExpand(new Recur[Temporal](rule), Instant.parse("2000-01-01T00:00:00Z"),
      Instant.parse("2026-04-08T10:00:00Z"), Instant.parse("2026-04-08T10:00:00Z"))).isFalse

  @ParameterizedTest
  @MethodSource(Array("realWorldRules"))
  def canExpandShouldAcceptRealWorldRules(rule: String): Unit =
    assertThat(RecurrenceExpansionGuard.canExpand(new Recur[Temporal](rule), Instant.parse("2000-01-01T00:00:00Z"),
      Instant.parse("2026-04-08T10:00:00Z"), Instant.parse("2026-04-08T10:00:00Z"))).isTrue
}
