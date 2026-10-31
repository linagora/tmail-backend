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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.linagora.tmail.dav.xml.DavMultistatus;
import com.linagora.tmail.dav.xml.XMLUtil;

class DavCalendarObjectTest {
    private static final String MULTISTATUS_WITH_SPACE_IN_HREF = """
        <d:multistatus xmlns:d="DAV:" xmlns:cal="urn:ietf:params:xml:ns:caldav"><d:response><d:href>/calendars/u/c/my event.ics</d:href><d:propstat><d:prop><d:getetag>"1"</d:getetag><cal:calendar-data>BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//test//EN
        BEGIN:VEVENT
        UID:1
        DTSTAMP:20250101T000000Z
        END:VEVENT
        END:VCALENDAR</cal:calendar-data></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>""";

    @Test
    void fromDavResponseShouldThrowDavClientExceptionWhenHrefIsNotAUri() {
        DavMultistatus multistatus = XMLUtil.parse(MULTISTATUS_WITH_SPACE_IN_HREF, DavMultistatus.class);

        assertThatThrownBy(() -> DavCalendarObject.fromDavResponse(multistatus.getResponses().getFirst()))
            .isInstanceOf(DavClientException.class);
    }
}
