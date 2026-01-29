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

package com.linagora.tmail.disconnector;

import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.events.Event;

public record DisconnectionRequested(EventId eventId, Set<Username> usernames) implements Event {
    public static DisconnectionRequested of(Set<Username> usernames) {
        return new DisconnectionRequested(EventId.random(), usernames);
    }

    private static final Username USERNAME = Username.of("disconnector");

    public boolean targetsAllUsers() {
        return usernames.isEmpty();
    }

    @Override
    public Username getUsername() {
        return USERNAME;
    }

    @Override
    public boolean isNoop() {
        return false;
    }

    @Override
    public EventId getEventId() {
        return eventId;
    }
}
