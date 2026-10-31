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

package com.linagora.tmail.dav.cal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.linagora.tmail.dav.DavUid;
import com.linagora.tmail.dav.OpenPaaSUserId;
import com.linagora.tmail.james.jmap.model.CalendarEventParsed;

class FreeBusyRequestTest {
    private static final OpenPaaSUserId USER_ID = new OpenPaaSUserId("abc");

    private static CalendarEventParsed parse(String vevent) {
        String ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            PRODID:-//test//EN
            BEGIN:VEVENT
            %sEND:VEVENT
            END:VCALENDAR
            """.formatted(vevent);
        return CalendarEventParsed.from(new ByteArrayInputStream(ics.getBytes(StandardCharsets.UTF_8))).head();
    }

    @Test
    void tryFromCalendarEventParsedShouldBuildRequestForEventWithPositiveDuration() {
        CalendarEventParsed event = parse("""
            UID:regular-event
            DTSTART:20250402T093000Z
            DTEND:20250402T103000Z
            """);

        assertThat(FreeBusyRequest.tryFromCalendarEventParsed(event)
                .map(builder -> builder.user(USER_ID).build()))
            .contains(FreeBusyRequest.builder()
                .start(Instant.parse("2025-04-02T09:30:00Z"))
                .end(Instant.parse("2025-04-02T10:30:00Z"))
                .user(USER_ID)
                .uid(new DavUid("regular-event"))
                .build());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "DURATION:PT0S\n",
        "DURATION:P0W\n",
        "DTEND:20250402T093000Z\n",
        "DTEND:20250402T083000Z\n"})
    void tryFromCalendarEventParsedShouldBeEmptyWhenEventEndIsNotAfterStart(String endProperty) {
        CalendarEventParsed event = parse("UID:zero-length-event\nDTSTART:20250402T093000Z\n" + endProperty);

        assertThat(FreeBusyRequest.tryFromCalendarEventParsed(event)).isEmpty();
    }

    @Test
    void tryFromCalendarEventParsedShouldBeEmptyWhenUidIsEmpty() {
        CalendarEventParsed event = parse("""
            UID:
            DTSTART:20250402T093000Z
            DTEND:20250402T103000Z
            """);

        assertThat(FreeBusyRequest.tryFromCalendarEventParsed(event)).isEmpty();
    }
}
