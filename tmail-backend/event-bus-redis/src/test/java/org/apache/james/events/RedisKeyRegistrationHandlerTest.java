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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

class RedisKeyRegistrationHandlerTest {
    private static final RegistrationKey KEY = new EventBusTestFixture.TestRegistrationKey("a");
    private static final EventListener.ReactiveEventListener LISTENER = EventListener.wrapReactive(event -> { });

    private RedisSetReactiveCommands<String, String> redisSetCommands;
    private LocalListenerRegistry localListenerRegistry;
    private RedisKeyRegistrationHandler testee;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisSetCommands = mock(RedisSetReactiveCommands.class);
        when(redisSetCommands.srem(anyString(), anyString())).thenReturn(Mono.just(1L));
        localListenerRegistry = new LocalListenerRegistry();
        RetryBackoffConfiguration noRetry = RetryBackoffConfiguration.builder()
            .maxRetries(0)
            .firstBackoff(Duration.ofMillis(1))
            .jitterFactor(0)
            .build();
        testee = new RedisKeyRegistrationHandler(new DefaultNamingStrategy(new EventBusName("test")), EventBusId.random(), null,
            null, localListenerRegistry, null, noRetry, new RecordingMetricFactory(), mock(RedisEventBusClientFactory.class),
            redisSetCommands, new RedisEventBusConfiguration(false, Duration.ofSeconds(10)));
    }

    @Test
    void registerCancelledDuringBindShouldRemoveTheListener() {
        when(redisSetCommands.sadd(anyString(), anyString())).thenReturn(Mono.never());

        Disposable subscription = testee.register(LISTENER, KEY).subscribe();
        assertThat(localListenerRegistry.getLocalListeners(KEY)).containsOnly(LISTENER);
        subscription.dispose();

        assertThat(localListenerRegistry.getLocalListeners(KEY)).isEmpty();
    }

    @Test
    void registerShouldRemoveTheListenerWhenTheBindFails() {
        when(redisSetCommands.sadd(anyString(), anyString())).thenReturn(Mono.error(new RuntimeException("Redis unavailable")));

        assertThatThrownBy(() -> testee.register(LISTENER, KEY).block())
            .isInstanceOf(RuntimeException.class);

        assertThat(localListenerRegistry.getLocalListeners(KEY)).isEmpty();
    }
}
