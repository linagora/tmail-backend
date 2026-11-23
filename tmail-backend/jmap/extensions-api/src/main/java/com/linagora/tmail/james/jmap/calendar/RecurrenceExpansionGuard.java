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


package com.linagora.tmail.james.jmap.calendar;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAmount;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.common.annotations.VisibleForTesting;

import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ExRule;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.transform.recurrence.Frequency;

/**
 * Tells whether ical4j can look up an occurrence of a series in bounded time and memory:
 * <ul>
 *     <li>Without COUNT, ical4j reaches the start of the walk by doubling an int multiplier of the rule's period. Past
 *     what this int holds, the multiplier overflows to 0 and the search never ends, nor can it be interrupted: whatever
 *     the interval, the last multiplier before the overflow goes at least 2^30 units of the frequency.</li>
 *     <li>With a COUNT, ical4j does not skip: it walks every period from DTSTART, keeping each candidate before the
 *     start of the walk. Periods without candidate do not count: COUNT bounds the walk only when every period holds
 *     one.</li>
 *     <li>ical4j starts the walk at the looked up date minus the span of the event (DURATION, or DTEND minus DTSTART),
 *     and collects every occurrence of the walk in a list.</li>
 *     <li>In each walked period, ical4j builds the whole product of the BY* lists, repeated values included, before
 *     filtering it.</li>
 * </ul>
 * The walked periods times the candidates of a period are bounded by {@link #MAX_EXPANDED_CANDIDATES}.
 */
public final class RecurrenceExpansionGuard {
    private static final long MAX_SKIPPED_UNITS = 1L << 30;
    // Every real world rule stays far below it: a rule on the five working days with a COUNT of 100,000 builds 500,000 candidates
    @VisibleForTesting
    static final long MAX_EXPANDED_CANDIDATES = 1_000_000L;
    private static final Set<Frequency> FINER_THAN_HOURLY = EnumSet.of(Frequency.SECONDLY, Frequency.MINUTELY);
    private static final Set<Frequency> FINER_THAN_DAILY = EnumSet.of(Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY);
    private static final Set<Frequency> FINER_THAN_WEEKLY = EnumSet.of(Frequency.SECONDLY, Frequency.MINUTELY, Frequency.HOURLY, Frequency.DAILY);
    private static final Map<Frequency, Duration> SHORTEST_UNIT = Map.of(
        Frequency.SECONDLY, Duration.ofSeconds(1),
        Frequency.MINUTELY, Duration.ofMinutes(1),
        Frequency.HOURLY, Duration.ofHours(1),
        Frequency.DAILY, Duration.ofDays(1),
        Frequency.WEEKLY, Duration.ofDays(7),
        Frequency.MONTHLY, Duration.ofDays(28),
        Frequency.YEARLY, Duration.ofDays(365));

    /**
     * @throws IllegalArgumentException when a RRULE or EXRULE of the master is too costly to expand up to the date, or
     * when the dates bounding the walk can not be computed
     */
    public static void checkOccurrenceCanBeLookedUp(VEvent master, Temporal recurrenceDate) {
        try {
            checkWalk(master, recurrenceDate);
        } catch (DateTimeException | ArithmeticException e) {
            throw new IllegalArgumentException("Cannot bound the expansion of the series up to " + recurrenceDate, e);
        }
    }

    private static void checkWalk(VEvent master, Temporal recurrenceDate) {
        Optional<DtStart<Temporal>> dtStart = master.getProperty(Property.DTSTART);
        Optional<Instant> seed = dtStart.map(DtStart::getDate).map(RecurrenceExpansionGuard::instant);
        if (seed.isEmpty()) {
            return;
        }
        Instant walkEnd = instant(recurrenceDate);
        Instant walkStart = minusAbsolute(walkEnd, span(master, seed.get()));
        Stream.concat(
                master.<RRule<?>>getProperties(Property.RRULE).stream().map(RRule::getRecur),
                master.<ExRule<?>>getProperties(Property.EXRULE).stream().map(ExRule::getRecur))
            .filter(recur -> !canExpand(recur, seed.get(), walkStart, walkEnd))
            .findAny()
            .ifPresent(recur -> {
                throw new IllegalArgumentException("Cannot expand " + recur + " from " + seed.get() + " over " + walkStart + " - " + walkEnd);
            });
    }

    @VisibleForTesting
    static boolean canExpand(Recur<?> recur, Instant seed, Instant walkStart, Instant walkEnd) {
        Instant from = seed;
        if (recur.getCount() <= 0) {
            if (units(recur, seed, walkStart) >= MAX_SKIPPED_UNITS) {
                return false;
            }
            from = seed.isAfter(walkStart) ? seed : walkStart;
        }
        long walkedPeriods = Math.max(0, units(recur, from, walkEnd) / Math.max(recur.getInterval(), 1)) + 1;
        if (recur.getCount() > 0 && !mayLeavePeriodsEmpty(recur)) {
            walkedPeriods = Math.min(walkedPeriods, recur.getCount());
        }
        return candidatesPerPeriod(recur) <= MAX_EXPANDED_CANDIDATES / walkedPeriods;
    }

    /**
     * Whether a period of the rule may hold no occurrence: COUNT then no longer bounds the walked periods. A BY* part
     * filters the candidates when it is as coarse as the frequency or coarser (eg BYMONTH of a DAILY rule), an ordinal
     * BYDAY may not exist in a month, and BYMONTHDAY, BYYEARDAY, BYWEEKNO or BYSETPOS may not either.
     */
    @VisibleForTesting
    static boolean mayLeavePeriodsEmpty(Recur<?> recur) {
        Frequency frequency = recur.getFrequency();
        return !recur.getSetPosList().isEmpty()
            || !recur.getMonthDayList().isEmpty()
            || !recur.getYearDayList().isEmpty()
            || !recur.getWeekNoList().isEmpty()
            || (!recur.getMonthList().isEmpty() && frequency != Frequency.YEARLY)
            || (!recur.getDayList().isEmpty() && (FINER_THAN_WEEKLY.contains(frequency) || recur.getDayList().stream().anyMatch(day -> day.getOffset() != 0)))
            || (!recur.getHourList().isEmpty() && FINER_THAN_DAILY.contains(frequency))
            || (!recur.getMinuteList().isEmpty() && FINER_THAN_HOURLY.contains(frequency))
            || (!recur.getSecondList().isEmpty() && frequency == Frequency.SECONDLY);
    }

    private static long units(Recur<?> recur, Instant from, Instant to) {
        return Duration.between(from, to).dividedBy(SHORTEST_UNIT.get(recur.getFrequency()));
    }

    /**
     * Bounds the candidates ical4j builds in a period, repeated values included. BYDAY stands for every matching day of
     * the period (53 weeks of a year, 5 of a month) and BYMONTHDAY of a YEARLY rule without BYMONTH for each month. A
     * list that only filters the candidates (eg BYMONTH of a DAILY rule) is counted as well: this errs on the safe side.
     */
    @VisibleForTesting
    static long candidatesPerPeriod(Recur<?> recur) {
        boolean yearly = recur.getFrequency() == Frequency.YEARLY;
        long dayWeight = switch (recur.getFrequency()) {
            case YEARLY -> recur.getWeekNoList().isEmpty() && recur.getMonthList().isEmpty() ? 53 : 5;
            case MONTHLY -> 5;
            default -> 1;
        };
        long monthDayWeight = yearly && recur.getMonthList().isEmpty() ? 12 : 1;
        return Stream.of(
                (long) recur.getSecondList().size(),
                (long) recur.getMinuteList().size(),
                (long) recur.getHourList().size(),
                recur.getDayList().size() * dayWeight,
                recur.getMonthDayList().size() * monthDayWeight,
                (long) recur.getYearDayList().size(),
                recur.getWeekNoList().size() * 7L,
                (long) recur.getMonthList().size())
            .mapToLong(Long::longValue)
            .filter(size -> size > 0)
            .reduce(1L, (a, b) -> Math.min(a * b, MAX_EXPANDED_CANDIDATES + 1));
    }

    private static TemporalAmount span(VEvent master, Instant seed) {
        Optional<net.fortuna.ical4j.model.property.Duration> duration = master.getProperty(Property.DURATION);
        if (duration.isPresent()) {
            return duration.get().getDuration();
        }
        Optional<DtEnd<Temporal>> dtEnd = master.getProperty(Property.DTEND);
        return dtEnd.map(DtEnd::getDate)
            .map(end -> (TemporalAmount) Duration.between(seed, instant(end)))
            .orElse(Duration.ZERO);
    }

    // The span may be negative (DTEND before DTSTART): the walk starts at the earlier of both. A span too large to be
    // subtracted throws a DateTimeException: the occurrence is not looked up
    private static Instant minusAbsolute(Instant instant, TemporalAmount span) {
        ZonedDateTime dateTime = instant.atZone(ZoneOffset.UTC);
        Instant before = dateTime.minus(span).toInstant();
        Instant after = dateTime.plus(span).toInstant();
        return before.isBefore(after) ? before : after;
    }

    // Floating dates are read in UTC: this only bounds the walk
    private static Instant instant(Temporal temporal) {
        return switch (temporal) {
            case LocalDate date -> date.atStartOfDay(ZoneOffset.UTC).toInstant();
            case LocalDateTime dateTime -> dateTime.toInstant(ZoneOffset.UTC);
            default -> Instant.from(temporal);
        };
    }

    private RecurrenceExpansionGuard() {
    }
}
