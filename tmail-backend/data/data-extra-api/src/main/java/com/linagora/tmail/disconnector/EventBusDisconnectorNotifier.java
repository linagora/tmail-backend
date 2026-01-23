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

import org.apache.james.DisconnectorNotifier;
import org.apache.james.events.EventBus;

public class EventBusDisconnectorNotifier implements DisconnectorNotifier {
    private final EventBus eventBus;

    public EventBusDisconnectorNotifier(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void disconnect(Request request) {
        DisconnectionRequested event = switch (request) {
            case MultipleUserRequest multipleUserRequest -> DisconnectionRequested.of(multipleUserRequest.usernameList());
            case AllUsersRequest ignored -> DisconnectionRequested.of(Set.of());
        };

        eventBus.dispatch(event, DisconnectorRegistrationKey.KEY).block();
    }
}
