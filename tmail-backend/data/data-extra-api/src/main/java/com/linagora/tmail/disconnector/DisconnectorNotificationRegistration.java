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

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.james.events.EventBus;
import org.apache.james.events.Registration;

import reactor.core.publisher.Mono;

@Singleton
public class DisconnectorNotificationRegistration {
    private final EventBus tmailEventBus;
    private final DisconnectionEventListener disconnectionEventListener;
    private final AtomicReference<Registration> registration;

    @Inject
    public DisconnectorNotificationRegistration(@Named("TMAIL_EVENT_BUS") EventBus tmailEventBus,
                                               DisconnectionEventListener disconnectionEventListener) {
        this.tmailEventBus = tmailEventBus;
        this.disconnectionEventListener = disconnectionEventListener;
        this.registration = new AtomicReference<>();
    }

    public void register() {
        if (registration.get() != null) {
            return;
        }
        registration.set(Mono.from(tmailEventBus.register(disconnectionEventListener, DisconnectorRegistrationKey.KEY))
            .block());
    }

    @PreDestroy
    public void unregister() {
        Optional.ofNullable(registration.getAndSet(null))
            .ifPresent(reg -> Mono.from(reg.unregister()).block());
    }
}
