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

import static org.apache.james.events.EventBusTestFixture.ALL_GROUPS;

import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisSentinelExtension;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Sender;

public class RabbitMQAndRedisEventBusWithRedisTLSSentinelTest implements GroupContract.SingleEventBusGroupContract, KeyContract.SingleEventBusKeyContract {
    static EventBusName TEST_EVENT_BUS = new EventBusName("test");
    static NamingStrategy TEST_NAMING_STRATEGY = new NamingStrategy(TEST_EVENT_BUS);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    @RegisterExtension
    static RedisSentinelExtension redisExtension = new RedisSentinelExtension(true);

    private RedisEventBusClientFactory redisEventBusClientFactory;
    private RabbitMQAndRedisEventBus eventBus;
    private EventSerializer eventSerializer;
    private RoutingKeyConverter routingKeyConverter;
    private MemoryEventDeadLetters memoryEventDeadLetters;
    private RedisClientFactory redisClientFactory;

    @Override
    public EnvironmentSpeedProfile getSpeedProfile() {
        return EnvironmentSpeedProfile.SLOW;
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @BeforeEach
    void setUp() throws Exception {
        redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisExtension.getRedisSentinelCluster().redisSentinelContainerList().getRedisConfiguration());
        redisEventBusClientFactory = new RedisEventBusClientFactory(redisClientFactory);
        memoryEventDeadLetters = new MemoryEventDeadLetters();

        eventSerializer = new EventBusTestFixture.TestEventSerializer();
        routingKeyConverter = RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory());

        eventBus = newEventBus();

        eventBus.start();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        Stream.concat(
                ALL_GROUPS.stream(),
                Stream.of(GroupRegistrationHandler.GROUP))
            .map(TEST_NAMING_STRATEGY::workQueue)
            .forEach(queueName -> rabbitMQExtension.getSender().delete(QueueSpecification.queue(queueName.asString())).block());
        rabbitMQExtension.getSender()
            .delete(ExchangeSpecification.exchange(TEST_NAMING_STRATEGY.exchange()))
            .block();
        rabbitMQExtension.getSender()
            .delete(TEST_NAMING_STRATEGY.deadLetterQueue())
            .block();
        redisClientFactory.close();
    }

    @Override
    @Test
    @Disabled("This test is failing by design as the different registration keys are handled by distinct messages")
    public void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {
    }

    private RabbitMQAndRedisEventBus newEventBus() throws Exception {
        return newEventBus(TEST_NAMING_STRATEGY, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider());
    }

    private RabbitMQAndRedisEventBus newEventBus(NamingStrategy namingStrategy, Sender sender, ReceiverProvider receiverProvider) throws Exception {
        return new RabbitMQAndRedisEventBus(namingStrategy, sender, receiverProvider, eventSerializer,
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, routingKeyConverter,
            memoryEventDeadLetters, new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            redisEventBusClientFactory,
            RedisEventBusConfiguration.DEFAULT);
    }
}
