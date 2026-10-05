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

import static org.apache.james.backends.rabbitmq.RabbitMQFixture.awaitAtMostOneMinute;
import static org.apache.james.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.events.EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION;
import static org.apache.james.events.EventBusTestFixture.newListener;
import static org.apache.james.events.RedisEventBusConfiguration.FAILURE_IGNORE_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.apache.james.backends.rabbitmq.RabbitMQConsumersHealthCheck;
import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableSet;

class PartitionedEventBusConsumersTest {
    private static final EventBusName EVENT_BUS_NAME = new EventBusName("partitionedConsumersTest");
    private static final List<NamingStrategy> NAMING_STRATEGIES = new TmailNamingStrategyFactory(EVENT_BUS_NAME,
        new TmailRabbitEventBusConfiguration(2)).namingStrategies();

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    private RedisClientFactory redisClientFactory;
    private RabbitMQAndRedisEventBus eventBus;
    private PartitionedEventBusConsumers testee;

    @BeforeEach
    void setUp() throws Exception {
        RedisConfiguration redisConfiguration = StandaloneRedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString());
        redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration);
        eventBus = new RabbitMQAndRedisEventBus(NAMING_STRATEGIES, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(),
            new TestEventSerializer(), RoutingKeyConverter.forFactories(new TestRegistrationKeyFactory()), new MemoryEventDeadLetters(),
            new RecordingMetricFactory(), rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(),
            new RabbitMQEventBus.Configurations(rabbitMQExtension.getRabbitMQ().getConfiguration(), RETRY_BACKOFF_CONFIGURATION),
            new RedisEventBusClientFactory(redisConfiguration, redisClientFactory),
            new RedisEventBusConfiguration(FAILURE_IGNORE_DEFAULT, Duration.ofSeconds(2)));
        eventBus.start();
        eventBus.register(newListener(), GROUP_A);

        testee = new PartitionedEventBusConsumers(eventBus, NAMING_STRATEGIES, rabbitMQExtension.getConnectionPool(),
            TmailGroupRegistrationHandler.GROUP);
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        redisClientFactory.close();
    }

    @Test
    void nameShouldBeTheBaseEventBusName() {
        assertThat(testee.name()).isEqualTo("partitionedConsumersTest event bus");
    }

    @Test
    void queuesShouldBeTheWorkQueuesOfEveryPartition() {
        assertThat(testee.queues()).containsExactlyInAnyOrder(
            NAMING_STRATEGIES.get(0).workQueue(GROUP_A).asString(),
            NAMING_STRATEGIES.get(0).workQueue(TmailGroupRegistrationHandler.GROUP).asString(),
            NAMING_STRATEGIES.get(1).workQueue(GROUP_A).asString(),
            NAMING_STRATEGIES.get(1).workQueue(TmailGroupRegistrationHandler.GROUP).asString());
    }

    @Test
    void consumersOfAStartedEventBusShouldBeHealthy() {
        RabbitMQConsumersHealthCheck healthCheck = new RabbitMQConsumersHealthCheck(ImmutableSet.of(testee));

        awaitAtMostOneMinute.untilAsserted(() -> assertThat(healthCheck.check().block().isHealthy()).isTrue());
    }
}
