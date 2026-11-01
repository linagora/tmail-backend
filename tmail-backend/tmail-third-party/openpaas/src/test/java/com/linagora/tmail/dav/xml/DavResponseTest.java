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

        assertThat(multistatus.getResponses().getFirst().getPropstat().getProp().getETag())
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

        assertThat(multistatus.getResponses().getFirst().getPropstat().getProp().getETag())
            .contains("\"2\"");
    }
}
