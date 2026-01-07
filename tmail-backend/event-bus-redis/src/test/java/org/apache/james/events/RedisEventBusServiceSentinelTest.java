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

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisSentinelExtension;
import org.apache.james.backends.redis.SentinelRedisConfiguration;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

public class RedisEventBusServiceSentinelTest {
    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @RegisterExtension
    static RedisSentinelExtension redisExtension = new RedisSentinelExtension();

    private RabbitMQAndRedisEventBus eventBus;

    @AfterEach
    void tearDown() {
        redisExtension.getRedisSentinelCluster().redisMasterReplicaContainerList().unPauseMasterNode();

        try {
            eventBus.stop();
        } catch (Exception e) {
            // Ignore exception
        }
    }

    @Test
    void dispatchShouldNotThrowWhenRedisMasterDownAndSentinelFailoverProcessKicksIn() throws Exception {
        // Given EventBus with failureIgnore set to false
        RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
        initEventBus(redisEventBusConfiguration);

        EventCollector listener = new EventCollector();
        EventBusTestFixture.GroupA registeredGroup = new EventBusTestFixture.GroupA();
        eventBus.register(listener, registeredGroup);

        //When Redis down
        redisExtension.getRedisSentinelCluster().redisMasterReplicaContainerList().pauseMasterNode();
        Thread.sleep(500);

        // Then dispatch should eventually succeed after sentinel failover process elects a new master
        Awaitility.await()
            .pollInterval(2, TimeUnit.SECONDS)
            .atMost(40, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThatCode(() -> eventBus.dispatch(EVENT, KEY_1).block())
                .doesNotThrowAnyException());
    }

    @Test
    void registerShouldNotThrowWhenRedisMasterDownAndSentinelFailoverProcessKicksIn() throws Exception {
        // Given EventBus with failureIgnore set to false
        RedisEventBusConfiguration redisEventBusConfiguration = new RedisEventBusConfiguration(false, Duration.ofSeconds(3));
        initEventBus(redisEventBusConfiguration);

        EventCollector listener = new EventCollector();

        //When Redis down
        redisExtension.getRedisSentinelCluster().redisMasterReplicaContainerList().pauseMasterNode();
        Thread.sleep(500);

        RegistrationKey KEY_1 = new EventBusTestFixture.TestRegistrationKey("a");

        // Then register should eventually succeed after sentinel failover process elects a new master
        Awaitility.await()
            .pollInterval(2, TimeUnit.SECONDS)
            .atMost(40, TimeUnit.SECONDS)
            .untilAsserted(() -> assertThatCode(() -> Mono.from(eventBus.register(listener, KEY_1)).block())
                .doesNotThrowAnyException());
    }

    private void initEventBus(RedisEventBusConfiguration redisEventBusConfiguration) throws Exception {
        SentinelRedisConfiguration redisConfiguration = redisExtension.getRedisSentinelCluster().redisSentinelContainerList().getRedisConfiguration();
        eventBus = new RabbitMQAndRedisEventBus(new NamingStrategy(new EventBusName("test")), rabbitMQExtension.getSender(),
            rabbitMQExtension.getReceiverProvider(), new EventBusTestFixture.TestEventSerializer(), RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()),
            new MemoryEventDeadLetters(), new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(),
            new RabbitMQEventBus.Configurations(rabbitMQExtension.getRabbitMQ().getConfiguration(), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION),
            new RedisEventBusClientFactory(redisConfiguration, new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration)),
            redisEventBusConfiguration);
        eventBus.start();
    }
}
