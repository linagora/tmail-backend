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
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import com.linagora.tmail.dav.xml.CalendarData;
import com.linagora.tmail.dav.xml.DavProp;
import com.linagora.tmail.dav.xml.DavResponse;
import com.linagora.tmail.james.jmap.calendar.CalendarEventModifier;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Uid;
import scala.jdk.javaapi.CollectionConverters;

public record DavCalendarObject(URI uri, Calendar calendarData, String eTag) {

    public static Optional<DavCalendarObject> fromDavResponse(DavResponse davResponse) {
        return davResponse.getProp()
            .flatMap(DavProp::getCalendarData)
            .map(CalendarData::getValue)
            .map(calendarData -> CalendarEventParsed.parseICal4jCalendar(
                IOUtils.toInputStream(calendarData, StandardCharsets.UTF_8)))
            .map(calendar ->
                new DavCalendarObject(
                    davResponse.getHrefValue().map(DavCalendarObject::asUri).orElseThrow(() ->
                        new DavClientException("Unable to find calendar object Href in dav response: " + davResponse)),
                    calendar, davResponse.getProp().flatMap(DavProp::getETag).orElse("ETag_NOT_FOUND")));
    }

    private static URI asUri(String href) {
        try {
            return URI.create(href);
        } catch (IllegalArgumentException e) {
            throw new DavClientException("Invalid calendar object Href in dav response: " + href, e);
        }
    }

    public CalendarEventParsed parse(Optional<String> recurrenceId) {
        List<CalendarEventParsed> events = CollectionConverters.asJava(CalendarEventParsed.from(calendarData()));
        if (events.isEmpty()) {
            throw new NoSuchElementException("No VEvents found in calendar object. Returning empty attendance results.");
        }

        return recurrenceId.flatMap(id -> events.stream()
                .filter(event -> id.equals(event.recurrenceIdAsJava().orElse("")))
                .findFirst())
            .orElse(events.getFirst());
    }

    public boolean hasVEventWithUid(DavUid eventUid) {
        return calendarData().getComponents(Component.VEVENT).stream()
            .filter(VEvent.class::isInstance)
            .map(VEvent.class::cast)
            .flatMap(event -> event.getUid().stream())
            .map(Uid::getValue)
            .anyMatch(eventUid.value()::equals);
    }

    public DavCalendarObject withUpdatePatches(CalendarEventModifier eventModifier) {
        return new DavCalendarObject(uri(), eventModifier.apply(calendarData()), eTag());
    }

}
