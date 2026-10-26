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
import java.time.ZoneId;
import java.time.zone.ZoneRules;
import java.util.Map;

import net.fortuna.ical4j.model.TimeZone;
import net.fortuna.ical4j.model.TimeZoneRegistry;
import net.fortuna.ical4j.model.TimeZoneRegistryImpl;

/**
 * Uses built-in rules for standard timezones to avoid exhausting the timezone ID pool.
 * Custom timezones keep the rules supplied in the calendar.
 */
public class ICal4JTimeZoneRegistry implements TimeZoneRegistry {
    private final TimeZoneRegistryImpl delegate = new TimeZoneRegistryImpl();

    @Override
    public void register(TimeZone timezone) {
        register(timezone, false);
    }

    @Override
    public void register(TimeZone timezone, boolean update) {
        try {
            TimeZoneRegistry.getGlobalZoneId(timezone.getID());
        } catch (DateTimeException e) {
            delegate.register(timezone, update);
        }
    }

    @Override
    public void clear() {
        delegate.clear();
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
