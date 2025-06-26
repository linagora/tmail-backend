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

import jakarta.xml.bind.annotation.XmlElement;

import com.google.common.base.MoreObjects;

public class DavResponse {
    @XmlElement(name = "href", namespace = "DAV:")
    private DavHref href;

    @XmlElement(name = "propstat", namespace = "DAV:")
    private DavPropstat propstat;

    public DavHref getHref() {
        return href;
    }

    public DavPropstat getPropstat() {
        return propstat;
    }

    public boolean isCalendarCollectionResponse() {
        return getPropstat()
            .getProp()
            .getResourceType()
            .map(DavResourceType::isCalendarCollection)
            .orElse(false);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
            .add("href", href)
            .add("propstat", propstat)
            .toString();
    }
}