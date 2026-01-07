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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.redis.DockerRedis;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisExtension;
import org.apache.james.backends.redis.StandaloneRedisConfiguration;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.lettuce.core.api.sync.RedisCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.QueueSpecification;

class CleanRedisEventBusServiceTest {
    private static final NamingStrategy TEST_NAMING_STRATEGY = new NamingStrategy(new EventBusName("test"));

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.WEAK);

    @RegisterExtension
    static RedisExtension redisExtension = new RedisExtension();

    private final RoutingKeyConverter routingKeyConverter = RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory());
    private final StandaloneRedisConfiguration redisConfiguration = StandaloneRedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString());
    private final RedisClientFactory redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(), redisConfiguration);
    private final CleanRedisEventBusService service = new CleanRedisEventBusService(
        new RedisEventBusClientFactory(redisConfiguration, redisClientFactory),
        RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()));

    private RabbitMQAndRedisEventBus eventBus1;
    private RabbitMQAndRedisEventBus eventBus2;
    private RabbitMQAndRedisEventBus eventBus3;

    @BeforeEach
    void setUp() throws Exception {
        eventBus1 = newEventBus();
        eventBus2 = newEventBus();
        eventBus3 = newEventBus();

        eventBus1.start();
        eventBus2.start();
        eventBus3.start();
    }

    @AfterEach
    void tearDown() {
        eventBus1.stop();
        eventBus2.stop();
        eventBus3.stop();
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
    }

    private RabbitMQAndRedisEventBus newEventBus() throws Exception {
        return new RabbitMQAndRedisEventBus(TEST_NAMING_STRATEGY, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider(), new EventBusTestFixture.TestEventSerializer(),
            routingKeyConverter, new MemoryEventDeadLetters(), new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(),
            new RabbitMQEventBus.Configurations(rabbitMQExtension.getRabbitMQ().getConfiguration(), EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION),
            new RedisEventBusClientFactory(redisConfiguration, redisClientFactory),
            RedisEventBusConfiguration.DEFAULT);
    }

    @Test
    void emptyCleanShouldSucceed() {
        service.cleanUp().block();

        assertThat(service.getContext().getTotalBindings()).isZero();
        assertThat(service.getContext().getDanglingBindings()).isZero();
        assertThat(service.getContext().getCleanedBindings()).isZero();
    }

    @Test
    void shouldCleanBindingsToInactiveChannels() {
        registerEventBusBinding(eventBus1, "registrationKey1");
        registerEventBusBinding(eventBus2, "registrationKey2");
        registerEventBusBinding(eventBus3, "registrationKey3");

        // shutdown the eventbus to make the channels inactive (a.k.a James nodes crashes and do not let client unregister the bindings)
        eventBus1.stop();
        eventBus2.stop();
        eventBus3.stop();

        // clean up
        service.cleanUp().block();

        assertThat(service.getContext().getTotalBindings()).isEqualTo(3);
        assertThat(service.getContext().getDanglingBindings()).isEqualTo(3);
        assertThat(service.getContext().getCleanedBindings()).isEqualTo(3);
    }

    @Test
    void shouldNotCleanBindingsToActiveChannels() {
        registerEventBusBinding(eventBus1, "registrationKey1");
        registerEventBusBinding(eventBus2, "registrationKey2");
        registerEventBusBinding(eventBus3, "registrationKey3");

        // clean up
        service.cleanUp().block();

        assertThat(service.getContext().getTotalBindings()).isEqualTo(3);
        assertThat(service.getContext().getDanglingBindings()).isZero();
        assertThat(service.getContext().getCleanedBindings()).isZero();
    }

    @Test
    void mixedCase() {
        registerEventBusBinding(eventBus1, "registrationKey1");
        registerEventBusBinding(eventBus2, "registrationKey2");
        registerEventBusBinding(eventBus3, "registrationKey3");

        // shutdown the eventbus to make the channels inactive (a.k.a James nodes crashes and do not let client unregister the bindings)
        eventBus3.stop();

        // clean up
        service.cleanUp().block();

        assertThat(service.getContext().getTotalBindings()).isEqualTo(3);
        assertThat(service.getContext().getDanglingBindings()).isEqualTo(1);
        assertThat(service.getContext().getCleanedBindings()).isEqualTo(1);
    }

    @Test
    void shouldNotImpactOtherApplicationSets(DockerRedis redis) {
        // Assume some 3rd party data using Redis Set
        RedisCommands<String, String> client = redis.createClient();
        client.sadd("rspamdKey", "value1", "value2");

        registerEventBusBinding(eventBus1, "registrationKey1");

        // shutdown the eventbus to make the channels inactive (a.k.a James nodes crashes and do not let client unregister the bindings)
        eventBus1.stop();

        // clean up
        service.cleanUp().block();
        assertThat(service.getContext().getTotalBindings()).isEqualTo(1);
        assertThat(service.getContext().getDanglingBindings()).isEqualTo(1);
        assertThat(service.getContext().getCleanedBindings()).isEqualTo(1);

        // should not impact the 3rd party Set
        assertThat(client.smembers("rspamdKey")).containsExactlyInAnyOrder("value1", "value2");
    }

    @Test
    void shouldBeAbleToScanBigNumberOfBindings() {
        Flux.fromStream(IntStream.rangeClosed(1 ,1000).boxed())
            .flatMap(i -> Mono.fromRunnable(() -> registerEventBusBinding(eventBus1, i.toString())))
            .then()
            .block();

        // clean up
        service.cleanUp().block();

        assertThat(service.getContext().getTotalBindings()).isEqualTo(1000);
        assertThat(service.getContext().getDanglingBindings()).isEqualTo(0);
        assertThat(service.getContext().getCleanedBindings()).isEqualTo(0);
    }

    @Test
    void shouldBeAbleToCleanBigNumberOfBindings() {
        Flux.fromStream(IntStream.rangeClosed(1 ,500).boxed())
            .flatMap(i -> Mono.fromRunnable(() -> registerEventBusBinding(eventBus1, i.toString())))
            .then()
            .block();
        Flux.fromStream(IntStream.rangeClosed(1 ,500).boxed())
            .flatMap(i -> Mono.fromRunnable(() -> registerEventBusBinding(eventBus2, i.toString())))
            .then()
            .block();

        // Make 500 bindings of eventBus1 dangling
        eventBus1.stop();

        // clean up
        service.cleanUp().block();

        assertThat(service.getContext().getTotalBindings()).isEqualTo(1000);
        assertThat(service.getContext().getDanglingBindings()).isEqualTo(500);
        assertThat(service.getContext().getCleanedBindings()).isEqualTo(500);
    }

    @Test
    void cleanUpInParallelShouldSucceed() {
        Flux.fromStream(IntStream.rangeClosed(1 ,500).boxed())
            .flatMap(i -> Mono.fromRunnable(() -> registerEventBusBinding(eventBus1, i.toString())))
            .then()
            .block();
        Flux.fromStream(IntStream.rangeClosed(1 ,500).boxed())
            .flatMap(i -> Mono.fromRunnable(() -> registerEventBusBinding(eventBus2, i.toString())))
            .then()
            .block();

        // Make 500 bindings of eventBus1 dangling
        eventBus1.stop();

        // Assume 3 James nodes clean up Redis data in parallel
        StandaloneRedisConfiguration redisConfiguration = StandaloneRedisConfiguration.from(redisExtension.dockerRedis().redisURI().toString());
        CleanRedisEventBusService node1 = new CleanRedisEventBusService(
            new RedisEventBusClientFactory(redisConfiguration, redisClientFactory),
            RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()));
        CleanRedisEventBusService node2 = new CleanRedisEventBusService(
            new RedisEventBusClientFactory(redisConfiguration, redisClientFactory),
            RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()));
        CleanRedisEventBusService node3 = new CleanRedisEventBusService(
            new RedisEventBusClientFactory(redisConfiguration, redisClientFactory),
            RoutingKeyConverter.forFactories(new EventBusTestFixture.TestRegistrationKeyFactory()));

        assertThatCode(() -> Flux.just(node1, node2, node3)
            .flatMap(CleanRedisEventBusService::cleanUp)
            .then()
            .block())
            .doesNotThrowAnyException();

        assertThat(node1.getContext().getCleanedBindings() + node2.getContext().getCleanedBindings() + node3.getContext().getCleanedBindings())
            .isEqualTo(500L);
    }

    private void registerEventBusBinding(RabbitMQAndRedisEventBus eventBus, String registrationKey) {
        // create 1 binding to the eventBus's channel
        Mono.from(eventBus.register(new EventCollector(), new EventBusTestFixture.TestRegistrationKey(registrationKey))).block();
    }
}
