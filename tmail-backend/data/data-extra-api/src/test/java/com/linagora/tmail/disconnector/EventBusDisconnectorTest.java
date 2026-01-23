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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.apache.james.DisconnectorNotifier;
import org.apache.james.core.Disconnector;
import org.apache.james.core.Username;
import org.apache.james.events.EventDeadLetters;
import org.apache.james.events.InVMEventBus;
import org.apache.james.events.MemoryEventDeadLetters;
import org.apache.james.events.RetryBackoffConfiguration;
import org.apache.james.events.delivery.InVmEventDelivery;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class EventBusDisconnectorTest {
    private static class RecordingDisconnector implements Disconnector {
        private final AtomicReference<Predicate<Username>> lastPredicate = new AtomicReference<>();

        @Override
        public void disconnect(Predicate<Username> username) {
            lastPredicate.set(username);
        }

        Predicate<Username> lastPredicate() {
            return lastPredicate.get();
        }
    }

    private InVMEventBus eventBus() {
        RecordingMetricFactory metricFactory = new RecordingMetricFactory();
        InVmEventDelivery eventDelivery = new InVmEventDelivery(metricFactory);
        EventDeadLetters eventDeadLetters = new MemoryEventDeadLetters();
        return new InVMEventBus(eventDelivery, RetryBackoffConfiguration.DEFAULT, eventDeadLetters);
    }

    @Test
    void disconnectShouldDisconnectAllUsersWhenAllUsersRequest() {
        InVMEventBus eventBus = eventBus();
        RecordingDisconnector disconnector = new RecordingDisconnector();

        Mono.from(eventBus.register(new DisconnectionEventListener(disconnector), DisconnectorRegistrationKey.KEY)).block();

        DisconnectorNotifier notifier = new EventBusDisconnectorNotifier(eventBus);
        notifier.disconnect(DisconnectorNotifier.AllUsersRequest.ALL_USERS_REQUEST);

        assertThat(disconnector.lastPredicate()).isNotNull();
        assertThat(disconnector.lastPredicate().test(Username.of("anyUser@domain.tld"))).isTrue();
        assertThat(disconnector.lastPredicate().test(Username.of("whateverUser@domain.tld"))).isTrue();
    }

    @Test
    void disconnectShouldOnlyDisconnectTargetUsersWhenMultipleUserRequest() {
        InVMEventBus eventBus = eventBus();
        RecordingDisconnector disconnector = new RecordingDisconnector();

        Mono.from(eventBus.register(new DisconnectionEventListener(disconnector), DisconnectorRegistrationKey.KEY)).block();

        DisconnectorNotifier notifier = new EventBusDisconnectorNotifier(eventBus);
        notifier.disconnect(DisconnectorNotifier.MultipleUserRequest.of(Set.of(
            Username.of("bob@domain.tld"),
            Username.of("alice@domain.tld"))));

        assertThat(disconnector.lastPredicate()).isNotNull();
        assertThat(disconnector.lastPredicate().test(Username.of("bob@domain.tld"))).isTrue();
        assertThat(disconnector.lastPredicate().test(Username.of("alice@domain.tld"))).isTrue();
        assertThat(disconnector.lastPredicate().test(Username.of("notDisconnected@domain.tld"))).isFalse();
    }
}
