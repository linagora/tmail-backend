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

import java.time.Instant;
import java.util.List;

import org.apache.commons.collections.CollectionUtils;

public record FreeBusyResponse(Instant start, Instant end, List<User> users) {

    public record User(String id, List<Calendar> calendars) {
    }

    public record Calendar(String id, List<BusyTime> busy) {

        public boolean isBusy() {
            return CollectionUtils.isNotEmpty(busy);
        }
    }

    public record BusyTime(String uid, Instant start, Instant end) {
    }

    public static FreeBusyResponse deserialize(byte[] bytes) {
        return FreeBusySerializer.INSTANCE.deserialize(bytes);
    }
}
