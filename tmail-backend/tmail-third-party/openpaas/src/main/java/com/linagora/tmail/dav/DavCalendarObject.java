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
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.linagora.tmail.dav.xml.CalendarData;
import com.linagora.tmail.dav.xml.DavResponse;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

import net.fortuna.ical4j.model.Calendar;

public record DavCalendarObject(URI uri, Calendar calendarData, String eTag) {

    public static Optional<DavCalendarObject> fromDavResponse(DavResponse davResponse) {
        return davResponse.getPropstat().getProp().getCalendarData()
            .map(DavCalendarObject::normalizeVCalendarData)
            .map(calendarData -> CalendarEventParsed.parseICal4jCalendar(
                IOUtils.toInputStream(calendarData, StandardCharsets.UTF_8)))
            .map(calendar ->
                new DavCalendarObject(
                    davResponse.getHref().getValue().map(URI::create).orElseThrow(() ->
                        new DavClientException("Unable to find calendar object Href in dav response: " + davResponse)),
                    calendar, davResponse.getPropstat().getProp().getETag().orElse("ETag_NOT_FOUND")));
    }

    private static String normalizeVCalendarData(CalendarData calendarData) {
        return calendarData.getValue();
//        return calendarData.getValue()
//            .lines()
//            .map(String::trim)
//            .filter(Predicate.not(String::isBlank))
//            .collect(Collectors.joining("\n"));
    }
}
