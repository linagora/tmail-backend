package org.apache.james.events;

import static org.apache.james.events.EventBusTestFixture.EVENT;
import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

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
        eventBus = new RabbitMQAndRedisEventBus(new NamingStrategy(new EventBusName("test")), rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(), new EventBusTestFixture.TestEventSerializer(),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()),
            new MemoryEventDeadLetters(), new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            new RedisEventBusClientFactory(RedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString(), false)),
            redisEventBusConfiguration);
        eventBus.start();

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
        eventBus = new RabbitMQAndRedisEventBus(new NamingStrategy(new EventBusName("test")), rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(), new EventBusTestFixture.TestEventSerializer(),
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()),
            new MemoryEventDeadLetters(), new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            new RedisEventBusClientFactory(RedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString(), false)),
            redisEventBusConfiguration);
        eventBus.start();

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

}
