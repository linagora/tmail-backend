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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.apache.james.core.Disconnector;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.util.ReactorUtils;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

@Singleton
public class DisconnectionEventListener implements EventListener.ReactiveEventListener {
    private final Disconnector disconnector;

    @Inject
    public DisconnectionEventListener(Disconnector disconnector) {
        this.disconnector = disconnector;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof DisconnectionRequested;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        return Mono.fromRunnable(() -> disconnectUsers(event))
            .subscribeOn(ReactorUtils.BLOCKING_CALL_WRAPPER)
            .then();
    }

    private void disconnectUsers(Event event) {
        DisconnectionRequested disconnectionRequested = (DisconnectionRequested) event;

        if (disconnectionRequested.targetsAllUsers()) {
            disconnector.disconnect(any -> true);
        } else {
            disconnector.disconnect(disconnectionRequested.usernames()::contains);
        }
    }
}
