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

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.XmlElement;

import com.linagora.tmail.dav.xml.resourcetypes.CalCalendar;
import com.linagora.tmail.dav.xml.resourcetypes.CsShared;
import com.linagora.tmail.dav.xml.resourcetypes.CsSubscribed;
import com.linagora.tmail.dav.xml.resourcetypes.DavCollection;
import com.linagora.tmail.dav.xml.resourcetypes.IResourceType;

public class DavResourceType {

    @XmlElement(name = "collection", namespace = "DAV:")
    private DavCollection collection;

    @XmlElement(name = "calendar", namespace = "urn:ietf:params:xml:ns:caldav")
    private CalCalendar calendar;

    // A subscription to the calendar of another user, eg one made public
    @XmlElement(name = "subscribed", namespace = "http://calendarserver.org/ns/")
    private CsSubscribed subscribed;

    // A calendar another user shares with this one, eg by delegation. Its owner sees it as shared-owner
    @XmlElement(name = "shared", namespace = "http://calendarserver.org/ns/")
    private CsShared shared;

    public List<IResourceType> getResourceTypes() {
        return Stream.of(collection, calendar, subscribed, shared)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     *  A calendar collection contains calendar object resources that represent calendar
     *  components within a calendar. A calendar collection is manifested to clients as a WebDAV
     *  resource collection identified by a URL. A calendar collection MUST report the DAV:collection
     *  and CALDAV:calendar XML elements in the value of the DAV: resourcetype property.
     *
     * @return {@code True} if this type refers to a calendar collection and {@code False} otherwise.
     * */
    public boolean isCalendarCollection() {
        List<IResourceType> resourceTypes = getResourceTypes();

        return resourceTypes.stream().anyMatch(DavCollection.class::isInstance) &&
               resourceTypes.stream().anyMatch(CalCalendar.class::isInstance);
    }

    /**
     * @return {@code True} if this type refers to a calendar collection the user owns, rather than one it subscribed to or
     * that another user shares with it.
     */
    public boolean isOwnedCalendarCollection() {
        return isCalendarCollection() && subscribed == null && shared == null;
    }
}
