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
import static org.apache.james.events.EventListener.ExecutionMode.ASYNCHRONOUS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.TEN_SECONDS;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

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

    static class TestEventCollector extends EventCollector {
        @Override
        public ExecutionMode getExecutionMode() {
            return ASYNCHRONOUS;
        }
    }

    public static void restartRedis(DockerRedis dockerRedis) {
        try {
            Field containerField = DockerRedis.class.getDeclaredField("container");
            containerField.setAccessible(true);
            GenericContainer<?> redisContainer = (GenericContainer<?>) containerField.get(dockerRedis);
            redisContainer.getDockerClient().restartContainerCmd(redisContainer.getContainerId()).exec();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access 'container' field", e);
        }
    }

    @Nested
    @Disabled("RedisEventBus tests upon Redis standalone restart. Please configure DockerRedis to expose to a static port on the host machine e.g:" +
        "redisContainer.setPortBindings(List.of(String.format(%d:%d, 6379, 6379)))")
    class RedisStandaloneRestart {
        @Nested
        class UserHasNoNotificationRegistrationBeforeRedisRestart {
            // test for user has no notification registration before Redis restart, the following behavior should be the same for Redis AOF disabled/enabled
            @Test
            void newNotificationRegistrationShouldWorkWellAfterRedisRestartTest(DockerRedis redis) throws Exception {
                TestEventCollector listener = new TestEventCollector();

                // Start event bus
                RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
                initEventBus(redisEventBusConfiguration);

                // Restart Redis before user subscribes notifications
                redis.stop();
                Thread.sleep(2000L); // simulate a downtime
                redis.start();

                // Await a bit for the Redis server to be fully started, and Redis Pub/sub connection can reconnect
                Thread.sleep(2000L);

                // User subscribes for IMAP notifications for example
                Mono.from(eventBus.register(listener, KEY_1)).block();
                eventBus.dispatch(EVENT, KEY_1).block();

                // The event should be received well
                await().timeout(TEN_SECONDS)
                    .untilAsserted(() -> assertThat(listener.getEvents()).containsOnly(EVENT));
            }
        }

        @Nested
        class UserHasNotificationRegistrationsBeforeRedisRestart {
            // Tests for user that has registrations before Redis restart

            @Nested
            // Note: We should disable Redis AOF and Redis RDB snapshot (enabled by default) in DockerRedis: `.withCommand("--appendonly", "no",  "--save", "")`
            // Otherwise, even with AOF disabled, data could still be persisted thanks to Redis RDB snapshot
            class RedisAOFDisabled {
                @Test
                void notificationShouldWorkAfterCleanupOldRegistrationAndResubscribe(DockerRedis redis) throws Exception {
                    TestEventCollector listener = new TestEventCollector();

                    // Start event bus
                    RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
                    initEventBus(redisEventBusConfiguration);

                    // User subscribes for IMAP notifications for example
                    Registration oldRegistration = Mono.from(eventBus.register(listener, KEY_1)).block();

                    // Restart Redis after user subscribes notifications
                    restartRedis(redis);

                    // Await a bit for the Redis server to be fully started, and Redis Pub/sub connection can reconnect
                    Thread.sleep(2000L);

                    // Old registration would lose after Redis restart (registration mappings is stored using redis SET), therefore user needs to re-subscribe a new registration (e.g F5 browser to refresh Websocket Push)
                    Mono.from(oldRegistration.unregister()).block(); // normally cleanup registration gets called as a part of IMAP SELECT/Websocket connection close
                    Mono.from(eventBus.register(listener, KEY_1)).block(); // User re-subscribe for notifications

                    // TMail dispatches an update
                    eventBus.dispatch(EVENT, KEY_1).block();

                    // The event should be received well
                    await().timeout(TEN_SECONDS)
                        .untilAsserted(() -> assertThat(listener.getEvents()).containsOnly(EVENT));
                }

                @Test
                void notificationShouldNotWorkIfOldRegistrationIsNotCleanedUpCorrectly(DockerRedis redis) throws Exception {
                    TestEventCollector listener = new TestEventCollector();

                    // Start event bus
                    RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
                    initEventBus(redisEventBusConfiguration);

                    // User subscribes for IMAP notifications for example
                    Registration oldRegistration = Mono.from(eventBus.register(listener, KEY_1)).block();

                    // Restart Redis after user subscribes notifications
                    restartRedis(redis);

                    // Await a bit for the Redis server to be fully started, and Redis Pub/sub connection can reconnect
                    Thread.sleep(2000L);

                    // Old registration would lose after Redis restart, therefore user needs to re-subscribe a new registration (e.g F5 browser to refresh Websocket Push)
                    // Somehow the old registration is not cleaned correctly (likely very rare case), and user tries to re-subscribe a new registration
                    Mono.from(eventBus.register(listener, KEY_1)).block();

                    // TMail dispatches an update
                    eventBus.dispatch(EVENT, KEY_1).block();

                    // The event would not be delivered because:
                    // 1. The registration mapping (stored using Redis Set) have been lost after Redis (without AOF) restart
                    // 2. This is not the first subscription for the KEY_1, therefore the registration mapping won't be stored again cf https://github.com/linagora/tmail-backend/blob/ec13090ac17601bd13651ee87508f14c412a68ee/tmail-backend/event-bus-redis/src/main/java/org/apache/james/events/RedisKeyRegistrationHandler.java#L149
                    await().timeout(TEN_SECONDS)
                        .untilAsserted(() -> assertThat(listener.getEvents()).isEmpty());
                }
            }

            @Nested
            // Note: We need to enable Redis AOF: `.withCommand("--appendonly", "yes")`
            class RedisAOFEnabled {
                @Test
                void notificationShouldWorkContinuouslyAfterRedisRestartWhenAOFEnabled(DockerRedis redis) throws Exception {
                    TestEventCollector listener = new TestEventCollector();

                    // Start event bus
                    RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
                    initEventBus(redisEventBusConfiguration);

                    // User subscribes for IMAP notifications for example
                    Mono.from(eventBus.register(listener, KEY_1)).block();

                    // Restart Redis after user subscribes notifications
                    restartRedis(redis);

                    // Await a bit for the Redis server to be fully started, and Redis Pub/sub connection can reconnect
                    Thread.sleep(3000L);

                    // TMail dispatches an update
                    eventBus.dispatch(EVENT, KEY_1).block();

                    // The notification should be received well
                    // as the registration is stored using Redis Set, and it would be restored after Redis (AOF enabled) restart
                    await().timeout(TEN_SECONDS)
                        .untilAsserted(() -> assertThat(listener.getEvents()).containsOnly(EVENT));
                }

                @Test
                void notificationShouldWorkEvenIfOldRegistrationIsNotCleanedUpCorrectly(DockerRedis redis) throws Exception {
                    TestEventCollector listener = new TestEventCollector();

                    // Start event bus
                    RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
                    initEventBus(redisEventBusConfiguration);

                    // User subscribes for IMAP notifications for example
                    Mono.from(eventBus.register(listener, KEY_1)).block();

                    // Restart Redis after user subscribes notifications
                    restartRedis(redis);

                    // Await a bit for the Redis server to be fully started, and Redis Pub/sub connection can reconnect
                    Thread.sleep(3000L);

                    // Somehow the old registration is not cleaned correctly (likely very rare case), and user tries to re-subscribe a new registration
                    Mono.from(eventBus.register(listener, KEY_1)).block();

                    // TMail dispatches an update
                    eventBus.dispatch(EVENT, KEY_1).block();

                    // The notification should be received well
                    // as the registration is stored using Redis Set, and it would be restored after Redis (AOF enabled) restart
                    await().timeout(TEN_SECONDS)
                        .untilAsserted(() -> assertThat(listener.getEvents()).containsOnly(EVENT));
                }
            }
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
            RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()),
            new MemoryEventDeadLetters(), new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(),
            new RabbitMQEventBus.Configurations(rabbitMQExtension.getRabbitMQ().getConfiguration(), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION),
            new RedisEventBusClientFactory(redisConfiguration, new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration)),
            redisEventBusConfiguration);
        eventBus.start();
    }
}
