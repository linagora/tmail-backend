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

import static org.apache.james.events.EventBusTestFixture.EVENT;
import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class RedisEventBusServiceTest {
    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    private RabbitMQAndRedisEventBus eventBus;

    @AfterEach
    void tearDown() {
        if (redisExtension.dockerRedis().isPaused()) {
            redisExtension.dockerRedis().unPause();
        }

        try {
            eventBus.stop();
        } catch (Exception e) {
            // Ignore exception
        }
    }

    @Test
    void dispatchShouldNotThrowWhenRedisDownAndFailureIgnoreIsTrue() throws Exception {
        // Given EventBus with failureIgnore set to true
        RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(true, Duration.ofSeconds(3));
        initEventBus(redisEventBusConfiguration);

        EventCollector listener = new EventCollector();
        EventBusTestFixture.GroupA registeredGroup = new EventBusTestFixture.GroupA();
        eventBus.register(listener, registeredGroup);

        //When Redis down
        redisExtension.dockerRedis().pause();
        Thread.sleep(500);

        // Then dispatch should not throw
        assertThatCode(() -> eventBus.dispatch(EVENT, KEY_1).block())
            .doesNotThrowAnyException();
    }

    @Test
    void dispatchShouldThrowWhenRedisDownAndFailureIgnoreIsFalse() throws Exception {
        // Given EventBus with failureIgnore set to false
        RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
        initEventBus(redisEventBusConfiguration);

        EventCollector listener = new EventCollector();
        EventBusTestFixture.GroupA registeredGroup = new EventBusTestFixture.GroupA();
        eventBus.register(listener, registeredGroup);

        //When Redis down
        redisExtension.dockerRedis().pause();

        Thread.sleep(500);
        // Then dispatch should throw
        assertThatThrownBy(() -> eventBus.dispatch(EVENT, KEY_1).block())
            .hasCauseInstanceOf(TimeoutException.class);
    }


    @Test
    void registerShouldNotThrowWhenRedisDownAndFailureIgnoreIsTrue() throws Exception {
        // Given EventBus with failureIgnore set to true
        RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(true, Duration.ofSeconds(3));
        initEventBus(redisEventBusConfiguration);

        EventCollector listener = new EventCollector();

        //When Redis down
        redisExtension.dockerRedis().pause();
        Thread.sleep(500);

        RegistrationKey KEY_1 = new EventBusTestFixture.TestRegistrationKey("a");

        // Then register should not throw
        assertThatCode(() -> Mono.from(eventBus.register(listener, KEY_1)).block())
            .doesNotThrowAnyException();
    }

    @Test
    void registerShouldThrowWhenRedisDownAndFailureIgnoreIsFalse() throws Exception {
        // Given EventBus with failureIgnore set to false
        RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
        initEventBus(redisEventBusConfiguration);

        EventCollector listener = new EventCollector();

        //When Redis down
        redisExtension.dockerRedis().pause();
        Thread.sleep(500);

        RegistrationKey KEY_1 = new EventBusTestFixture.TestRegistrationKey("a");

        // Then register should throw
        assertThatThrownBy(() -> Mono.from(eventBus.register(listener, KEY_1)).block())
            .hasCauseInstanceOf(TimeoutException.class);
    }

    private void initEventBus(RedisEventBusConfiguration redisEventBusConfiguration) throws Exception {
        StandaloneRedisConfiguration redisConfiguration = StandaloneRedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString());
        eventBus = new RabbitMQAndRedisEventBus(new NamingStrategy(new EventBusName("test")), rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(), new EventBusTestFixture.TestEventSerializer(),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()),
            new MemoryEventDeadLetters(), new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            new RedisEventBusClientFactory(redisConfiguration, new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration)),
            redisEventBusConfiguration);
        eventBus.start();
    }
}
