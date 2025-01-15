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

package com.linagora.tmail.dav.request;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public record GetCalendarByEventIdRequestBody(String vEventUid) {

    public String value() {
        return """
            <C:calendar-query xmlns:C="urn:ietf:params:xml:ns:caldav">
                  <D:prop
                    xmlns:D="DAV:">
                    <D:getetag/>
                    <C:calendar-data/>
                  </D:prop>
                  <C:filter>
                    <C:comp-filter name="VCALENDAR">
                      <C:comp-filter name="VEVENT">
                        <C:prop-filter name="UID">
                          <C:text-match collation="i;octet">%s</C:text-match>
                        </C:prop-filter>
                      </C:comp-filter>
                    </C:comp-filter>
                  </C:filter>
            </C:calendar-query>
            """.formatted(vEventUid);
    }

    public ByteBuf asByteBuf() {
        return Unpooled.wrappedBuffer(value().getBytes());
    }
}
