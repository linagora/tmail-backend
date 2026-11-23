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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.zone.ZoneRules;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.google.common.collect.ImmutableList;

import net.fortuna.ical4j.data.DefaultComponentFactorySupplier;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentFactory;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryImpl;
import net.fortuna.ical4j.model.component.Observance;
import net.fortuna.ical4j.model.component.VTimeZone;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.TzId;

/**
 * Uses built-in rules for standard timezones to avoid exhausting the timezone ID pool.
 * Custom timezones keep the rules supplied in the calendar.
 *
 * ical4j takes an ID out of a JVM wide pool (1500 by default) for each registered custom timezone, and only gives it
 * back once the registry is garbage collected, not when it is cleared: the custom timezones are counted against a
 * budget ({@value #MAX_CUSTOM_TIMEZONES} by default) that registries may share, eg the calendars of a mail. A repeated
 * TZID is registered once, the first definition being kept, update or not. A rejected timezone does not count. Neither
 * budget is given back by {@link #clear()}.
 *
 * Building a timezone with several STANDARD (or DAYLIGHT) observances, and registering a custom one, makes ical4j
 * materialize all the occurrences of their recurrence rules. The parser builds the timezone before registering it, a
 * standard TZID included: the cost of the rules is bounded as soon as the parser builds the VTIMEZONE, by the
 * {@link #componentFactories()} of this registry. One instance serves one calendar: the limit applies to all its
 * timezones. A rule above the limit throws an exception, which the ical4j parser reports as a ParserException.
 */
public class ICal4JTimeZoneRegistry implements TimeZoneRegistry {
    // ical4j walks every period of a rule from its DTSTART, building all the candidates of a period (eg a year) at once.
    // Regular rules build a handful of candidates per year: this leaves room for hundreds of timezones starting in 1601
    private static final long MAX_EXPANDED_CANDIDATES_PER_CALENDAR = 1_000_000;
    private static final long MAX_CANDIDATES_PER_PERIOD = 1_000;
    // Observance#getLatestOnset expands the rules up to 10 years ahead
    private static final int HORIZON_IN_YEARS = 10;
    // Invitations carry one timezone per distinct TZID they use, often a single one
    public static final int MAX_CUSTOM_TIMEZONES = 20;

    private final TimeZoneRegistryImpl delegate = new TimeZoneRegistryImpl();
    private final Set<String> customTimezoneIds = new HashSet<>();
    private final Set<Observance> checkedObservances = Collections.newSetFromMap(new IdentityHashMap<>());
    private final AtomicInteger remainingCustomTimezones;
    private long remainingExpandedCandidates = MAX_EXPANDED_CANDIDATES_PER_CALENDAR;

    public ICal4JTimeZoneRegistry() {
        this(newCustomTimezoneBudget());
    }

    /**
     * @param remainingCustomTimezones the custom timezones this registry may still register, shared with other registries
     */
    public ICal4JTimeZoneRegistry(AtomicInteger remainingCustomTimezones) {
        this.remainingCustomTimezones = remainingCustomTimezones;
    }

    public static AtomicInteger newCustomTimezoneBudget() {
        return new AtomicInteger(MAX_CUSTOM_TIMEZONES);
    }

    /**
     * The ical4j component factories, a VTIMEZONE being rejected by this registry when its rules are too costly.
     */
    public List<ComponentFactory<?>> componentFactories() {
        return Stream.concat(
                new DefaultComponentFactorySupplier().get().stream()
                    .filter(factory -> !factory.supports(Component.VTIMEZONE)),
                Stream.of(new CheckedVTimeZoneFactory()))
            .collect(ImmutableList.toImmutableList());
    }

    private class CheckedVTimeZoneFactory extends VTimeZone.Factory {
        private static final long serialVersionUID = 1L;

        @Override
        public VTimeZone createComponent(PropertyList properties) {
            return checked(super.createComponent(properties));
        }

        @Override
        public VTimeZone createComponent(PropertyList properties, ComponentList<?> subComponents) {
            return checked(super.createComponent(properties, subComponents));
        }

        private VTimeZone checked(VTimeZone timezone) {
            assertRecurrenceCostIsBounded(timezone, observancesExpandedWhenBuilt(timezone));
            return timezone;
        }
    }

    @Override
    public void register(TimeZone timezone) {
        register(timezone, false);
    }

    @Override
    public void register(TimeZone timezone, boolean update) {
        try {
            TimeZoneRegistry.getGlobalZoneId(timezone.getID());
        } catch (DateTimeException e) {
            if (customTimezoneIds.contains(timezone.getID())) {
                return;
            }
            assertRecurrenceCostIsBounded(timezone.getVTimeZone(), timezone.getVTimeZone().getObservances());
            reserveCustomTimezone(timezone);
            try {
                delegate.register(timezone, update);
            } catch (RuntimeException registrationFailure) {
                remainingCustomTimezones.incrementAndGet();
                throw registrationFailure;
            }
            customTimezoneIds.add(timezone.getID());
        }
    }

    // The budget does not go below 0: a rejected timezone takes no slot
    private void reserveCustomTimezone(TimeZone timezone) {
        if (remainingCustomTimezones.getAndUpdate(remaining -> Math.max(remaining - 1, 0)) <= 0) {
            throw new IllegalArgumentException("Too many custom timezones, " + timezone.getID() + " is not registered");
        }
    }

    // Mirrors TimeZone#getRawOffset, which looks for the latest onset of several STANDARD (or DAYLIGHT) observances
    private static List<Observance> observancesExpandedWhenBuilt(VTimeZone timezone) {
        List<Observance> standards = timezone.getComponents(Observance.STANDARD);
        List<Observance> seasonalTimes = standards.isEmpty() ? timezone.getComponents(Observance.DAYLIGHT) : standards;
        if (seasonalTimes.size() > 1) {
            return seasonalTimes;
        }
        return List.of();
    }

    // An observance expanded when its timezone is built, then when it is registered, is counted once
    private void assertRecurrenceCostIsBounded(VTimeZone timezone, List<Observance> observances) {
        String tzId = timezone.<TzId>getProperty(Property.TZID).map(TzId::getValue).orElse("without TZID");
        for (Observance observance : observances) {
            if (!checkedObservances.contains(observance)) {
                List<RRule<OffsetDateTime>> rrules = observance.getProperties(Property.RRULE);
                for (RRule<OffsetDateTime> rrule : rrules) {
                    assertRecurrenceCostIsBounded(tzId, rrule.getRecur(), initialOnset(observance));
                }
                checkedObservances.add(observance);
            }
        }
    }

    // Estimated without expanding the rule: limiting the emitted occurrences would not bound the walked periods
    private void assertRecurrenceCostIsBounded(String tzId, Recur<OffsetDateTime> recur, Instant seed) {
        if (RecurrenceExpansionGuard.candidatesPerPeriod(recur) > MAX_CANDIDATES_PER_PERIOD) {
            throw new IllegalArgumentException("Too many BY* combinations in a recurrence rule of timezone " + tzId);
        }
        Instant now = Instant.now();
        Instant end = (seed.isAfter(now) ? seed : now).atOffset(ZoneOffset.UTC).plusYears(HORIZON_IN_YEARS).toInstant();
        remainingExpandedCandidates -= Math.min(RecurrenceExpansionGuard.expansionCost(recur, seed, end), remainingExpandedCandidates + 1);
        if (remainingExpandedCandidates < 0) {
            throw new IllegalArgumentException("Too costly recurrence rules in timezone " + tzId);
        }
    }

    // Mirrors Observance#getLatestOnset, including its default when DTSTART is missing
    private Instant initialOnset(Observance observance) {
        Optional<DtStart<Temporal>> dtStart = observance.getProperty(Property.DTSTART);
        return dtStart.map(DtStart::getDate)
            .map(ICal4JTimeZoneRegistry::asUtcOnset)
            .orElse(Instant.EPOCH);
    }

    // A UTC DTSTART (eg 16010101T000000Z) is read as an Instant
    private static Instant asUtcOnset(Temporal date) {
        if (date.isSupported(ChronoField.INSTANT_SECONDS)) {
            return Instant.from(date);
        }
        if (date.isSupported(ChronoField.HOUR_OF_DAY)) {
            return LocalDateTime.from(date).toInstant(ZoneOffset.UTC);
        }
        return LocalDate.from(date).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    @Override
    public void clear() {
        delegate.clear();
        customTimezoneIds.clear();
        checkedObservances.clear();
    }

    @Override
    public TimeZone getTimeZone(String id) {
        return delegate.getTimeZone(id);
    }

    @Override
    public Map<String, ZoneRules> getZoneRules() {
        return delegate.getZoneRules();
    }

    @Override
    public ZoneId getZoneId(String tzId) {
        try {
            return delegate.getZoneId(tzId);
        } catch (DateTimeException e) {
            if (e.getMessage().contains("Unknown timezone identifier")) {
                return TimeZoneRegistry.getGlobalZoneId(tzId);
            }
            throw e;
        }
    }

    @Override
    public String getTzId(String zoneId) {
        return delegate.getTzId(zoneId);
    }
}
