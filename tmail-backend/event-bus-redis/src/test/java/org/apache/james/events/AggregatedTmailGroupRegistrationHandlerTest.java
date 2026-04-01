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

package org.apache.james.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

public class AggregatedTmailGroupRegistrationHandlerTest {
    private static class TestingTmailGroupRegistrationHandler implements TmailGroupRegistrationHandler {
        private final Collection<Group> registeredGroups;
        private final Collection<GroupRegistration> synchronousGroupRegistrations;
        private final AtomicInteger registerCount;
        private final AtomicInteger unregisterCount;
        private final AtomicInteger redeliverCount;

        private TestingTmailGroupRegistrationHandler() {
            this(List.of());
        }

        private TestingTmailGroupRegistrationHandler(Collection<GroupRegistration> synchronousGroupRegistrations) {
            this.registeredGroups = new ArrayList<>();
            this.synchronousGroupRegistrations = synchronousGroupRegistrations;
            this.registerCount = new AtomicInteger();
            this.unregisterCount = new AtomicInteger();
            this.redeliverCount = new AtomicInteger();
        }

        @Override
        public Stream<GroupRegistration> synchronousGroupRegistrations() {
            return synchronousGroupRegistrations.stream();
        }

        @Override
        public Mono<Void> reDeliver(Group group, Event event) {
            redeliverCount.incrementAndGet();
            return Mono.empty();
        }

        @Override
        public Registration register(EventListener.ReactiveEventListener listener, Group group) {
            registerCount.incrementAndGet();
            registeredGroups.add(group);
            return () -> Mono.fromRunnable(() -> {
                unregisterCount.incrementAndGet();
                registeredGroups.remove(group);
            });
        }

        @Override
        public Collection<Group> registeredGroups() {
            return registeredGroups;
        }

        @Override
        public void stop() {
        }

        @Override
        public void restart() {
        }
    }

    private static class FailingTmailGroupRegistrationHandler extends TestingTmailGroupRegistrationHandler {
        @Override
        public Registration register(EventListener.ReactiveEventListener listener, Group group) {
            throw new RuntimeException("failure");
        }
    }

    private static final Group GROUP = new Group() {
    };
    private static final Event EVENT = EventBusTestFixture.EVENT;
    private static final EventListener.ReactiveEventListener LISTENER = EventListener.wrapReactive(EventBusTestFixture.newListener());

    @Test
    void registerShouldRegisterAndUnregisterAllDelegates() {
        TestingTmailGroupRegistrationHandler first = new TestingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler second = new TestingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler third = new TestingTmailGroupRegistrationHandler();

        AggregatedTmailGroupRegistrationHandler testee = new AggregatedTmailGroupRegistrationHandler(List.of(first, second, third));

        Registration registration = testee.register(LISTENER, GROUP);
        Mono.from(registration.unregister()).block();

        assertThat(first.registerCount).hasValue(1);
        assertThat(second.registerCount).hasValue(1);
        assertThat(third.registerCount).hasValue(1);
        assertThat(first.unregisterCount).hasValue(1);
        assertThat(second.unregisterCount).hasValue(1);
        assertThat(third.unregisterCount).hasValue(1);
    }

    @Test
    void shouldReturnRegisteredGroups() {
        TestingTmailGroupRegistrationHandler first = new TestingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler second = new TestingTmailGroupRegistrationHandler();
        first.register(LISTENER, GROUP);
        second.register(LISTENER, GROUP);

        AggregatedTmailGroupRegistrationHandler testee = new AggregatedTmailGroupRegistrationHandler(List.of(first, second));

        assertThat(testee.registeredGroups()).containsExactly(GROUP);
    }

    @Test
    void shouldReturnSynchronousGroupRegistrations() {
        GroupRegistration firstRegistration = mock(GroupRegistration.class);
        GroupRegistration secondRegistration = mock(GroupRegistration.class);
        TestingTmailGroupRegistrationHandler first = new TestingTmailGroupRegistrationHandler(List.of(firstRegistration, secondRegistration));
        TestingTmailGroupRegistrationHandler second = new TestingTmailGroupRegistrationHandler(List.of(firstRegistration, secondRegistration));

        AggregatedTmailGroupRegistrationHandler testee = new AggregatedTmailGroupRegistrationHandler(List.of(first, second));

        assertThat(testee.synchronousGroupRegistrations()).containsExactlyInAnyOrder(firstRegistration, secondRegistration);
    }

    @Test
    void reDeliverShouldDelegateToSinglePartitionRandomly() {
        TestingTmailGroupRegistrationHandler first = new TestingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler second = new TestingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler third = new TestingTmailGroupRegistrationHandler();

        AggregatedTmailGroupRegistrationHandler testee = new AggregatedTmailGroupRegistrationHandler(List.of(first, second, third));

        testee.reDeliver(GROUP, EVENT).block();

        assertThat(first.redeliverCount.get() + second.redeliverCount.get() + third.redeliverCount.get()).isEqualTo(1);
    }

    @Test
    void splitConcurrencyConfigurationShouldSplitConcurrencyAcrossPartitions() {
        RabbitMQEventBus.Configurations configurations = new RabbitMQEventBus.Configurations(
            mock(RabbitMQConfiguration.class),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION,
            new EventBus.Configuration(10, Optional.of(Duration.ofMinutes(1))));

        RabbitMQEventBus.Configurations splitConfiguration = AggregatedTmailGroupRegistrationHandler.splitConcurrencyConfiguration(configurations, 3);

        assertThat(splitConfiguration.eventBusConfiguration().maxConcurrency()).isEqualTo(3);
        assertThat(splitConfiguration.eventBusConfiguration().executionTimeout()).isEqualTo(Optional.of(Duration.ofMinutes(1)));
    }

    @Test
    void splitConcurrencyConfigurationShouldKeepAtLeastOneConcurrency() {
        RabbitMQEventBus.Configurations configurations = new RabbitMQEventBus.Configurations(
            mock(RabbitMQConfiguration.class),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION,
            new EventBus.Configuration(2, Optional.empty()));

        RabbitMQEventBus.Configurations splitConfiguration = AggregatedTmailGroupRegistrationHandler.splitConcurrencyConfiguration(configurations, 3);

        assertThat(splitConfiguration.eventBusConfiguration().maxConcurrency()).isEqualTo(1);
    }

    @Test
    void registerShouldRollbackSuccessfulDelegatesWhenLaterDelegateFails() {
        TestingTmailGroupRegistrationHandler first = new TestingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler failing = new FailingTmailGroupRegistrationHandler();
        TestingTmailGroupRegistrationHandler third = new TestingTmailGroupRegistrationHandler();

        AggregatedTmailGroupRegistrationHandler testee = new AggregatedTmailGroupRegistrationHandler(List.of(first, failing, third));

        assertThatThrownBy(() -> testee.register(LISTENER, GROUP))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("failure");

        assertThat(first.registerCount).hasValue(1);
        assertThat(first.unregisterCount).hasValue(1);
        assertThat(third.registerCount).hasValue(0);
        assertThat(third.unregisterCount).hasValue(0);
    }

    @Test
    void splitConcurrencyConfigurationShouldRejectNonPositivePartitionCount() {
        RabbitMQEventBus.Configurations configurations = new RabbitMQEventBus.Configurations(
            mock(RabbitMQConfiguration.class),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION,
            new EventBus.Configuration(2, Optional.empty()));

        assertThatThrownBy(() -> AggregatedTmailGroupRegistrationHandler.splitConcurrencyConfiguration(configurations, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Partition count should be strictly positive");
    }

}
