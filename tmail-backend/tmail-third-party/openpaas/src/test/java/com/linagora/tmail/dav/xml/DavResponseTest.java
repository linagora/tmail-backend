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

package com.linagora.tmail.dav.xml;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DavResponseTest {

    @Test
    void getPropstatShouldFallbackToPropstatWithoutStatus() {
        DavMultistatus multistatus = XMLUtil.parse(
            "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/calendars/u/c/x.ics</d:href>"
                + "<d:propstat><d:prop><d:getetag>\"1\"</d:getetag></d:prop></d:propstat></d:response></d:multistatus>",
            DavMultistatus.class);

        assertThat(multistatus.getResponses().getFirst().getProp().flatMap(DavProp::getETag))
            .contains("\"1\"");
    }

    @Test
    void getPropstatShouldPreferSuccessfulPropstatOverOneWithoutStatus() {
        DavMultistatus multistatus = XMLUtil.parse(
            "<d:multistatus xmlns:d=\"DAV:\"><d:response><d:href>/calendars/u/c/x.ics</d:href>"
                + "<d:propstat><d:prop><d:getetag>\"1\"</d:getetag></d:prop></d:propstat>"
                + "<d:propstat><d:prop><d:getetag>\"2\"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat>"
                + "</d:response></d:multistatus>",
            DavMultistatus.class);

        assertThat(multistatus.getResponses().getFirst().getProp().flatMap(DavProp::getETag))
            .contains("\"2\"");
    }

    private static DavResponse calendarResponse(String resourceTypes) {
        return XMLUtil.parse(
            "<d:multistatus xmlns:d=\"DAV:\" xmlns:cal=\"urn:ietf:params:xml:ns:caldav\" xmlns:cs=\"http://calendarserver.org/ns/\">"
                + "<d:response><d:href>/calendars/u/c/</d:href><d:propstat><d:prop><d:resourcetype>" + resourceTypes + "</d:resourcetype></d:prop>"
                + "<d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response></d:multistatus>",
            DavMultistatus.class).getResponses().getFirst();
    }

    @Test
    void ownedCalendarShouldBeRecognized() {
        DavResponse response = calendarResponse("<d:collection/><cal:calendar/>");

        assertThat(response.isOwnedCalendarCollectionResponse()).isTrue();
    }

    @Test
    void calendarSharedByItsOwnerShouldBeOwned() {
        DavResponse response = calendarResponse("<d:collection/><cal:calendar/><cs:shared-owner/>");

        assertThat(response.isOwnedCalendarCollectionResponse()).isTrue();
    }

    @Test
    void subscribedCalendarShouldNotBeOwned() {
        DavResponse response = calendarResponse("<d:collection/><cal:calendar/><cs:subscribed/>");

        assertThat(response.isCalendarCollectionResponse()).isTrue();
        assertThat(response.isOwnedCalendarCollectionResponse()).isFalse();
    }

    @Test
    void calendarSharedWithTheUserShouldNotBeOwned() {
        DavResponse response = calendarResponse("<d:collection/><cal:calendar/><cs:shared/>");

        assertThat(response.isCalendarCollectionResponse()).isTrue();
        assertThat(response.isOwnedCalendarCollectionResponse()).isFalse();
    }
}
