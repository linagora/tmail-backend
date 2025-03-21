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

package com.linagora.tmail.dav;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.linagora.tmail.dav.xml.CalendarData;
import com.linagora.tmail.dav.xml.DavResponse;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.ComponentList;
import net.fortuna.ical4j.model.RelationshipPropertyModifiers;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.parameter.PartStat;
import scala.jdk.javaapi.CollectionConverters;

public record DavCalendarObject(URI uri, Calendar calendarData, String eTag) {
    private static final Logger LOGGER = LoggerFactory.getLogger(DavCalendarObject.class);

    public static Optional<DavCalendarObject> fromDavResponse(DavResponse davResponse) {
        return davResponse.getPropstat().getProp().getCalendarData()
            .map(CalendarData::getValue)
            .map(calendarData -> CalendarEventParsed.parseICal4jCalendar(
                IOUtils.toInputStream(calendarData, StandardCharsets.UTF_8)))
            .map(calendar ->
                new DavCalendarObject(
                    davResponse.getHref().getValue().map(URI::create).orElseThrow(() ->
                        new DavClientException("Unable to find calendar object Href in dav response: " + davResponse)),
                    calendar, davResponse.getPropstat().getProp().getETag().orElse("ETag_NOT_FOUND")));
    }

    public CalendarEventParsed parse() {
        List<CalendarEventParsed> events = CollectionConverters.asJava(CalendarEventParsed.from(calendarData()));
        if (events.isEmpty()) {
            throw new RuntimeException("No VEvents found in calendar object. Returning empty attendance results.");
        }
        if (events.size() != 1) {
            LOGGER.debug("Expected exactly one VEvent, but found {} entries. Using the first VEvent. " +
                    "This may indicate unhandled recurrent events or a malformed calendar object. VEvents: {}",
                events.size(), events);
        }
        return events.getFirst();
    }

    public DavCalendarObject withPartStat(String targetAttendeeEmail, PartStat partStat) {
        LOGGER.trace("Calendar to update: {}", calendarData());
        Calendar updatedCalendarData = calendarData().copy();

        updatedCalendarData.setComponentList(new ComponentList<>(updatedCalendarData.<VEvent>getComponents(Component.VEVENT)
            .stream()
            .map(vEvent -> updatedVEvent(targetAttendeeEmail, partStat, vEvent))
            .toList()));

        LOGGER.trace("Calendar updated: {}", updatedCalendarData);

        return new DavCalendarObject(uri(), updatedCalendarData, eTag());
    }

    public DavCalendarObject withRescheduledTiming(ZonedDateTime statDateTime, ZonedDateTime endDateTime, Consumer<VEvent> vEventValidator) {
        Calendar updatedCalendarData = CalendarEventModifier.modifyEventTiming(calendarData(), statDateTime, endDateTime, vEventValidator);
        return new DavCalendarObject(uri(), updatedCalendarData, eTag());
    }

    private VEvent updatedVEvent(String targetAttendeeEmail, PartStat partStat, VEvent vEvent) {
        return vEvent.getAttendees()
            .stream()
            .filter(attendee -> attendee.getCalAddress().toASCIIString().equalsIgnoreCase("mailto:" + targetAttendeeEmail))
            .findAny()
            .map(attendee -> (VEvent) vEvent.with(RelationshipPropertyModifiers.ATTENDEE, attendee.replace(partStat)))
            .orElse(vEvent);
    }

}
