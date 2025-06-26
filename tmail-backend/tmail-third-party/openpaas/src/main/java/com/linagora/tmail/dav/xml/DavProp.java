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

import java.util.Optional;

import jakarta.xml.bind.annotation.XmlElement;

import com.google.common.base.MoreObjects;

public class DavProp {
    @XmlElement(name = "calendar-data", namespace = "urn:ietf:params:xml:ns:caldav")
    private CalendarData calendarData;

    @XmlElement(name = "getetag", namespace = "DAV:")
    private DavGetETag getETag;

    @XmlElement(name = "resourcetype", namespace = "DAV:")
    private DavResourceType resourceType;

    public Optional<CalendarData> getCalendarData() {
        return Optional.ofNullable(calendarData);
    }

    public Optional<String> getETag() {
        return Optional.ofNullable(getETag).map(DavGetETag::getValue);
    }

    public Optional<DavResourceType> getResourceType() {
        return Optional.ofNullable(resourceType);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("calendarData", calendarData)
            .add("getETag", getETag)
            .toString();
    }
}