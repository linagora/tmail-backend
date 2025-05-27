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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package org.apache.james.events;

import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.QueueArguments.NO_ARGUMENTS;
import static org.apache.james.events.EventBusConcurrentTestContract.newCountingListener;
import static org.apache.james.events.EventBusTestFixture.ALL_GROUPS;
import static org.apache.james.events.EventBusTestFixture.EVENT;
import static org.apache.james.events.EventBusTestFixture.EVENT_2;
import static org.apache.james.events.EventBusTestFixture.GROUP_A;
import static org.apache.james.events.EventBusTestFixture.GROUP_B;
import static org.apache.james.events.EventBusTestFixture.KEY_1;
import static org.apache.james.events.EventBusTestFixture.NO_KEYS;
import static org.apache.james.events.EventBusTestFixture.newListener;
import static org.apache.james.events.RedisEventBusConfiguration.FAILURE_IGNORE_DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.awaitility.Durations.TEN_MINUTES;
import static org.awaitility.Durations.TEN_SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.james.backends.rabbitmq.RabbitMQExtension;
import org.apache.james.backends.rabbitmq.RabbitMQFixture;
import org.apache.james.backends.rabbitmq.RabbitMQManagementAPI;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.backends.redis.RedisClientFactory;
import org.apache.james.backends.redis.RedisConfiguration;
import org.apache.james.events.EventBusTestFixture.EventListenerCountingSuccessfulExecution;
import org.apache.james.events.EventBusTestFixture.GroupA;
import org.apache.james.events.EventBusTestFixture.TestEventSerializer;
import org.apache.james.events.EventBusTestFixture.TestRegistrationKeyFactory;
import org.apache.james.events.RoutingKeyConverter.RoutingKey;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.apache.james.server.core.filesystem.FileSystemImpl;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.data.Percentage;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.stubbing.Answer;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;

abstract class RabbitMQAndRedisEventBusContractTest implements GroupContract.SingleEventBusGroupContract, GroupContract.MultipleEventBusGroupContract,
    KeyContract.SingleEventBusKeyContract, KeyContract.MultipleEventBusKeyContract,
    ErrorHandlingContract {
    
    static EventBusName TEST_EVENT_BUS = new EventBusName("test");
    static NamingStrategy TEST_NAMING_STRATEGY = new NamingStrategy(TEST_EVENT_BUS);
    static DispatchingFailureGroup dispatchingFailureGroup = new DispatchingFailureGroup(TEST_EVENT_BUS);

    @RegisterExtension
    static RabbitMQExtension rabbitMQExtension = RabbitMQExtension.singletonRabbitMQ()
        .isolationPolicy(RabbitMQExtension.IsolationPolicy.STRONG);

    private RedisEventBusClientFactory redisEventBusClientFactory;
    private RabbitMQAndRedisEventBus eventBus;
    private RabbitMQAndRedisEventBus eventBus2;
    private RabbitMQAndRedisEventBus eventBus3;
    private RabbitMQAndRedisEventBus eventBusWithKeyHandlerNotStarted;
    private EventSerializer eventSerializer;
    private RoutingKeyConverter routingKeyConverter;
    private MemoryEventDeadLetters memoryEventDeadLetters;
    private RedisClientFactory redisClientFactory;

    @Override
    public EnvironmentSpeedProfile getSpeedProfile() {
        return EnvironmentSpeedProfile.SLOW;
    }

    abstract RedisConfiguration redisConfiguration();

    abstract void pauseRedis();

    abstract void unpauseRedis();

    @BeforeEach
    void setUp() throws Exception {
        redisClientFactory = new RedisClientFactory(FileSystemImpl.forTesting(),
            redisConfiguration());
        redisEventBusClientFactory = new RedisEventBusClientFactory(redisConfiguration(), redisClientFactory);
        memoryEventDeadLetters = new MemoryEventDeadLetters();

        eventSerializer = new TestEventSerializer();
        routingKeyConverter = RoutingKeyConverter.forFactories(new TestRegistrationKeyFactory());

        eventBus = newEventBus();
        eventBus2 = newEventBus();
        eventBus3 = newEventBus();
        eventBusWithKeyHandlerNotStarted = newEventBus();

        eventBus.start();
        eventBus2.start();
        eventBus3.start();
        eventBusWithKeyHandlerNotStarted.startWithoutStartingKeyRegistrationHandler();
    }

    @AfterEach
    void tearDown() {
        eventBus.stop();
        eventBus2.stop();
        eventBus3.stop();
        eventBusWithKeyHandlerNotStarted.stop();
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

    private RabbitMQAndRedisEventBus newEventBus() throws Exception {
        return newEventBus(TEST_NAMING_STRATEGY, rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider());
    }

    private RabbitMQAndRedisEventBus newEventBus(NamingStrategy namingStrategy, Sender sender, ReceiverProvider receiverProvider) throws Exception {
        return new RabbitMQAndRedisEventBus(namingStrategy, sender, receiverProvider, eventSerializer,
            EventBusTestFixture.RETRY_BACKOFF_CONFIGURATION, routingKeyConverter,
            memoryEventDeadLetters, new RecordingMetricFactory(),
            rabbitMQExtension.getRabbitChannelPool(), EventBusId.random(), rabbitMQExtension.getRabbitMQ().getConfiguration(),
            redisEventBusClientFactory,
            new RedisEventBusConfiguration(FAILURE_IGNORE_DEFAULT, Duration.ofSeconds(2)));
    }

    @Override
    public EventBus eventBus() {
        return eventBus;
    }

    @Override
    public EventBus eventBus2() {
        return eventBus2;
    }

    @Override
    public EventDeadLetters deadLetter() {
        return memoryEventDeadLetters;
    }

    @Override
    @Test
    @Disabled("This test is failing by design as the different registration keys are handled by distinct messages")
    public void dispatchShouldCallListenerOnceWhenSeveralKeysMatching() {

    }

    @Test
    void groupQueuesNameShouldRemainUnchanged() {
        // to detect breaking change on Group queues name
        assertThat(new NamingStrategy(new EventBusName("mailboxEvent"))
            .workQueue(TmailGroupRegistrationHandler.GROUP)
            .asString())
            .isEqualTo("mailboxEvent-workQueue-org.apache.james.events.TmailGroupRegistrationHandler$GroupRegistrationHandlerGroup");
    }

    @Test
    void eventProcessingShouldNotCrashOnInvalidMessage() {
        EventCollector listener = new EventCollector();
        GroupA registeredGroup = new GroupA();
        eventBus.register(listener, registeredGroup);

        String emptyRoutingKey = "";
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(TEST_NAMING_STRATEGY.exchange(),
                emptyRoutingKey,
                "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
            .block();

        eventBus.dispatch(EVENT, NO_KEYS).block();
        await()
            .timeout(TEN_SECONDS).untilAsserted(() ->
                assertThat(listener.getEvents()).containsOnly(EVENT));
    }

    @Test
    void eventProcessingShouldNotCrashOnInvalidMessages() {
        EventCollector listener = new EventCollector();
        GroupA registeredGroup = new GroupA();
        eventBus.register(listener, registeredGroup);

        String emptyRoutingKey = "";
        IntStream.range(0, 10).forEach(i -> rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(TEST_NAMING_STRATEGY.exchange(),
                emptyRoutingKey,
                "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
            .block());

        eventBus.dispatch(EVENT, NO_KEYS).block();
        await()
            .timeout(TEN_SECONDS).untilAsserted(() ->
            assertThat(listener.getEvents()).containsOnly(EVENT));
    }

    @Test
    void eventProcessingShouldStoreInvalidMessagesInDeadLetterQueue() {
        EventCollector listener = new EventCollector();
        GroupA registeredGroup = new GroupA();
        eventBus.register(listener, registeredGroup);

        String emptyRoutingKey = "";
        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(TEST_NAMING_STRATEGY.exchange(),
                emptyRoutingKey,
                "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
            .block();

        AtomicInteger deadLetteredCount = new AtomicInteger();
        rabbitMQExtension.getRabbitChannelPool()
            .createReceiver()
            .consumeAutoAck(TEST_NAMING_STRATEGY.deadLetterQueue().getName())
            .doOnNext(next -> deadLetteredCount.incrementAndGet())
            .subscribeOn(Schedulers.newSingle("test"))
            .subscribe();

        Awaitility.await().atMost(TEN_SECONDS)
            .untilAsserted(() -> assertThat(deadLetteredCount.get()).isEqualTo(1));
    }

    @Test
    void registrationShouldNotCrashOnInvalidMessage() {
        EventCollector listener = new EventCollector();
        Mono.from(eventBus.register(listener, KEY_1)).block();

        rabbitMQExtension.getSender()
            .send(Mono.just(new OutboundMessage(TEST_NAMING_STRATEGY.exchange(),
                RoutingKey.of(KEY_1).asString(),
                "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
            .block();

        eventBus.dispatch(EVENT, KEY_1).block();
        await().timeout(TEN_SECONDS)
            .untilAsserted(() -> assertThat(listener.getEvents()).containsOnly(EVENT));
    }

    @Test
    void registrationShouldNotCrashOnInvalidMessages() {
        EventCollector listener = new EventCollector();
        Mono.from(eventBus.register(listener, KEY_1)).block();

        IntStream.range(0, 100)
            .forEach(i -> rabbitMQExtension.getSender()
                .send(Mono.just(new OutboundMessage(TEST_NAMING_STRATEGY.exchange(),
                    RoutingKey.of(KEY_1).asString(),
                    "BAD_PAYLOAD!".getBytes(StandardCharsets.UTF_8))))
                .block());

        eventBus.dispatch(EVENT, KEY_1).block();
        await().timeout(TEN_SECONDS)
            .untilAsserted(() -> assertThat(listener.getEvents()).containsOnly(EVENT));
    }

    @Test
    void deserializeEventCollectorGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.events.EventCollector$EventCollectorGroup"))
            .isEqualTo(new EventCollector.EventCollectorGroup());
    }

    @Test
    void registerGroupShouldCreateRetryExchange() throws Exception {
        EventListener listener = newListener();
        GroupA registeredGroup = new GroupA();
        eventBus.register(listener, registeredGroup);

        GroupConsumerRetry.RetryExchangeName retryExchangeName = TEST_NAMING_STRATEGY.retryExchange(registeredGroup);
        assertThat(rabbitMQExtension.managementAPI().listExchanges())
            .anyMatch(exchange -> exchange.getName().equals(retryExchangeName.asString()));
    }

    @Nested
    class ConcurrentTest implements EventBusConcurrentTestContract.MultiEventBusConcurrentContract,
        EventBusConcurrentTestContract.SingleEventBusConcurrentContract {

        @Override
        public EnvironmentSpeedProfile getSpeedProfile() {
            return EnvironmentSpeedProfile.SLOW;
        }

        @Test
        void rabbitMQAndRedisEventBusShouldHandleBulksGracefully() throws Exception {
            EventListenerCountingSuccessfulExecution countingListener1 = newCountingListener();
            eventBus().register(countingListener1, new GroupA());
            int totalGlobalRegistrations = 1; // GroupA

            int threadCount = 10;
            int operationCount = 10000;
            int totalDispatchOperations = threadCount * operationCount;
            eventBus = (RabbitMQAndRedisEventBus) eventBus();
            ConcurrentTestRunner.builder()
                .reactorOperation((threadNumber, operationNumber) -> eventBus.dispatch(EVENT, NO_KEYS))
                .threadCount(threadCount)
                .operationCount(operationCount)
                .runSuccessfullyWithin(Duration.ofMinutes(3));

            await()
                .pollInterval(FIVE_SECONDS)
                .timeout(TEN_MINUTES).untilAsserted(() ->
                    assertThat(countingListener1.numberOfEventCalls()).isEqualTo((totalGlobalRegistrations * totalDispatchOperations)));
        }

        @Override
        public EventBus eventBus3() {
            return eventBus3;
        }

        @Override
        public EventBus eventBus2() {
            return eventBus2;
        }

        @Override
        public EventBus eventBus() {
            return eventBus;
        }
    }

    @Nested
    class AtLeastOnceTest {

        @Test
        void inProcessingEventShouldBeReDispatchedToAnotherEventBusWhenOneIsDown() {
            EventListenerCountingSuccessfulExecution eventBusListener = spy(new EventListenerCountingSuccessfulExecution());
            EventListenerCountingSuccessfulExecution eventBus2Listener = spy(new EventListenerCountingSuccessfulExecution());
            EventListenerCountingSuccessfulExecution eventBus3Listener = spy(new EventListenerCountingSuccessfulExecution());
            Answer<?> callEventAndSleepForever = invocation -> {
                invocation.callRealMethod();
                TimeUnit.SECONDS.sleep(Long.MAX_VALUE);
                return null;
            };

            doAnswer(callEventAndSleepForever).when(eventBusListener).event(any());
            doAnswer(callEventAndSleepForever).when(eventBus2Listener).event(any());

            eventBus.register(eventBusListener, GROUP_A);
            eventBus2.register(eventBus2Listener, GROUP_A);
            eventBus3.register(eventBus3Listener, GROUP_A);

            eventBus.dispatch(EVENT, NO_KEYS).block();
            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(eventBusListener.numberOfEventCalls()).isEqualTo(1));
            eventBus.stop();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(eventBus2Listener.numberOfEventCalls()).isEqualTo(1));
            eventBus2.stop();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(eventBus3Listener.numberOfEventCalls()).isEqualTo(1));
        }
    }

    @Nested
    class PublishingTest {
        private static final String WORK_QUEUE_NAME = "test-workQueue";

        @BeforeEach
        void setUp() {
            Sender sender = rabbitMQExtension.getSender();

            sender.declareQueue(QueueSpecification.queue(WORK_QUEUE_NAME)
                .durable(DURABLE)
                .exclusive(!EXCLUSIVE)
                .autoDelete(!AUTO_DELETE)
                .arguments(NO_ARGUMENTS))
                .block();
            sender.bind(BindingSpecification.binding()
                .exchange(TEST_NAMING_STRATEGY.exchange())
                .queue(WORK_QUEUE_NAME)
                .routingKey(EMPTY_ROUTING_KEY))
                .block();
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQ() {
            eventBus.dispatch(EVENT, NO_KEYS).block();

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        @Test
        void dispatchShouldPublishSerializedEventToRabbitMQWhenNotBlocking() {
            eventBus.dispatch(EVENT, NO_KEYS).block();

            assertThat(dequeueEvent()).isEqualTo(EVENT);
        }

        private Event dequeueEvent() {
            try (Receiver receiver = rabbitMQExtension.getReceiverProvider().createReceiver()) {
                byte[] eventInBytes = receiver.consumeAutoAck(WORK_QUEUE_NAME)
                    .blockFirst()
                    .getBody();

                return eventSerializer.asEvent(new String(eventInBytes, StandardCharsets.UTF_8));
            }
        }
    }

    @Nested
    class LifeCycleTest {
        private static final int THREAD_COUNT = 10;
        private static final int OPERATION_COUNT = 100000;

        private RabbitMQManagementAPI rabbitManagementAPI;

        @BeforeEach
        void setUp() throws Exception {
            rabbitManagementAPI = rabbitMQExtension.managementAPI();
        }

        @AfterEach
        void tearDown() {
            rabbitMQExtension.getRabbitMQ().unpause();
        }

        @Nested
        class SingleEventBus {

            @Test
            void startShouldCreateEventExchange() {
                eventBus.start();
                assertThat(rabbitManagementAPI.listExchanges())
                    .filteredOn(exchange -> exchange.getName().equals(TEST_NAMING_STRATEGY.exchange()))
                    .hasOnlyOneElementSatisfying(exchange -> {
                        assertThat(exchange.isDurable()).isTrue();
                        assertThat(exchange.getType()).isEqualTo(DIRECT_EXCHANGE);
                    });
            }

            @Test
            void dispatchShouldWorkAfterRestartForOldGroupRegistration() throws Exception {
                eventBus.start();
                EventListener listener = newListener();
                eventBus.register(listener, GROUP_A);

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.dispatch(EVENT, NO_KEYS).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterRestartForNewGroupRegistration() throws Exception {
                eventBus.start();
                EventListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.register(listener, GROUP_A);

                eventBus.dispatch(EVENT, NO_KEYS).block();

                assertThatListenerReceiveOneEvent(listener);

            }

            @Test
            void redeliverShouldWorkAfterRestartForOldGroupRegistration() throws Exception {
                eventBus.start();
                EventListener listener = newListener();
                eventBus.register(listener, GROUP_A);

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.reDeliver(GROUP_A, EVENT).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void redeliverShouldWorkAfterRestartForNewGroupRegistration() throws Exception {
                eventBus.start();
                EventListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().restart();

                eventBus.register(listener, GROUP_A);

                eventBus.reDeliver(GROUP_A, EVENT).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterNetworkIssuesForNewGroupRegistration() {
                eventBus.start();
                EventListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().pause();

                assertThatThrownBy(() -> eventBus.dispatch(EVENT, NO_KEYS).block())
                        .getCause()
                        .isInstanceOf(NoSuchElementException.class)
                        .hasMessageContaining("Timeout waiting for idle object");

                rabbitMQExtension.getRabbitMQ().unpause();

                eventBus.register(listener, GROUP_A);
                eventBus.dispatch(EVENT, NO_KEYS).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void redeliverShouldWorkAfterNetworkIssuesForNewGroupRegistration() {
                eventBus.start();
                EventListener listener = newListener();

                rabbitMQExtension.getRabbitMQ().pause();

                assertThatThrownBy(() -> eventBus.reDeliver(GROUP_A, EVENT).block())
                    .isInstanceOf(GroupRegistrationNotFound.class);

                rabbitMQExtension.getRabbitMQ().unpause();

                eventBus.register(listener, GROUP_A);
                eventBus.reDeliver(GROUP_A, EVENT).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterNetworkIssuesForOldKeyRegistration() {
                eventBus.start();
                EventListener listener = newListener();
                when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);
                Mono.from(eventBus.register(listener, KEY_1)).block();

                pauseRedis();

                assertThatThrownBy(() -> eventBus.dispatch(EVENT, KEY_1).block())
                    .getCause()
                    .isInstanceOf(TimeoutException.class);

                unpauseRedis();

                eventBus.dispatch(EVENT, KEY_1).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void dispatchShouldWorkAfterNetworkIssuesForNewKeyRegistration() {
                eventBus.start();
                EventListener listener = newListener();
                when(listener.getExecutionMode()).thenReturn(EventListener.ExecutionMode.ASYNCHRONOUS);

                pauseRedis();

                assertThatThrownBy(() -> eventBus.dispatch(EVENT, KEY_1).block())
                    .getCause()
                    .isInstanceOf(TimeoutException.class);

                unpauseRedis();

                Mono.from(eventBus.register(listener, KEY_1)).block();
                eventBus.dispatch(EVENT, KEY_1).block();
                assertThatListenerReceiveOneEvent(listener);
            }

            @Test
            void stopShouldNotDeleteEventBusExchange() {
                eventBus.start();
                eventBus.stop();

                assertThat(rabbitManagementAPI.listExchanges())
                    .anySatisfy(exchange -> assertThat(exchange.getName()).isEqualTo(TEST_NAMING_STRATEGY.exchange()));
            }

            @Test
            void stopShouldNotDeleteGroupRegistrationWorkQueue() {
                eventBus.start();
                eventBus.register(mock(EventListener.class), GROUP_A);
                eventBus.stop();

                assertThat(rabbitManagementAPI.listQueues())
                    .anySatisfy(queue -> assertThat(queue.getName()).contains(GroupA.class.getName()));
            }

            @Test
            void eventBusShouldNotThrowWhenContinuouslyStartAndStop() {
                assertThatCode(() -> {
                    eventBus.start();
                    eventBus.stop();
                    eventBus.stop();
                    eventBus.start();
                    eventBus.start();
                    eventBus.start();
                    eventBus.stop();
                    eventBus.stop();
                }).doesNotThrowAnyException();
            }

            @Test
            void dispatchShouldStopDeliveringEventsShortlyAfterStopIsCalled() throws Exception {
                eventBus.start();

                EventListenerCountingSuccessfulExecution listener = new EventListenerCountingSuccessfulExecution();
                eventBus.register(listener, GROUP_A);

                try (Closeable closeable = ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1).block())
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .noErrorLogs()
                    .run()) {

                    TimeUnit.SECONDS.sleep(2);

                    eventBus.stop();
                    eventBus2.stop();
                    int callsAfterStop = listener.numberOfEventCalls();

                    TimeUnit.SECONDS.sleep(1);
                    assertThat(listener.numberOfEventCalls())
                        .isCloseTo(callsAfterStop, Percentage.withPercentage(2));
                }
            }
        }

        @Nested
        class MultiEventBus {

            @Test
            void multipleEventBusStartShouldCreateOnlyOneEventExchange() {
                assertThat(rabbitManagementAPI.listExchanges())
                    .filteredOn(exchange -> exchange.getName().equals(TEST_NAMING_STRATEGY.exchange()))
                    .hasSize(1);
            }

            @Test
            void multipleEventBusShouldNotThrowWhenStartAndStopContinuously() {
                assertThatCode(() -> {
                    eventBus.start();
                    eventBus.start();
                    eventBus2.start();
                    eventBus2.start();
                    eventBus.stop();
                    eventBus.stop();
                    eventBus.stop();
                    eventBus3.start();
                    eventBus3.start();
                    eventBus3.start();
                    eventBus3.stop();
                    eventBus.start();
                    eventBus2.start();
                    eventBus.stop();
                    eventBus2.stop();
                }).doesNotThrowAnyException();
            }

            @Test
            void multipleEventBusStopShouldNotDeleteEventBusExchange() {
                eventBus.stop();
                eventBus2.stop();
                eventBus3.stop();
                eventBusWithKeyHandlerNotStarted.stop();

                assertThat(rabbitManagementAPI.listExchanges())
                    .anySatisfy(exchange -> assertThat(exchange.getName()).isEqualTo(TEST_NAMING_STRATEGY.exchange()));
            }

            @Test
            void multipleEventBusStopShouldNotDeleteGroupRegistrationWorkQueue() {
                eventBus.register(mock(EventListener.class), GROUP_A);

                eventBus.stop();
                eventBus2.stop();
                eventBus3.stop();
                eventBusWithKeyHandlerNotStarted.stop();

                assertThat(rabbitManagementAPI.listQueues())
                    .anySatisfy(queue -> assertThat(queue.getName()).contains(GroupA.class.getName()));
            }

            @Test
            void multipleEventBusStopShouldDeleteAllKeyRegistrationsWorkQueue() {
                eventBus.stop();
                eventBus2.stop();
                eventBus3.stop();
                eventBusWithKeyHandlerNotStarted.stop();

                assertThat(rabbitManagementAPI.listQueues())
                    .filteredOn(queue -> !queue.getName().startsWith("test-")
                        && !queue.getName().startsWith("other-"))
                    .isEmpty();
            }

            @Test
            void dispatchShouldStopDeliveringEventsShortlyAfterStopIsCalled() throws Exception {
                eventBus.start();
                eventBus2.start();

                EventListenerCountingSuccessfulExecution listener = new EventListenerCountingSuccessfulExecution();
                eventBus.register(listener, GROUP_A);
                eventBus2.register(listener, GROUP_A);

                try (Closeable closeable = ConcurrentTestRunner.builder()
                    .operation((threadNumber, step) -> eventBus.dispatch(EVENT, KEY_1).block())
                    .threadCount(THREAD_COUNT)
                    .operationCount(OPERATION_COUNT)
                    .noErrorLogs()
                    .run()) {

                    TimeUnit.SECONDS.sleep(2);

                    eventBus.stop();
                    eventBus2.stop();
                    int callsAfterStop = listener.numberOfEventCalls();

                    TimeUnit.SECONDS.sleep(1);
                    assertThat(listener.numberOfEventCalls())
                        .isCloseTo(callsAfterStop, Percentage.withPercentage(2));
                }
            }
        }

    }

    @Nested
    class IsolationTest {
        private RabbitMQAndRedisEventBus otherEventBus;

        @BeforeEach
        void beforeEach() throws Exception {
            otherEventBus = newEventBus(new NamingStrategy(new EventBusName("other")), rabbitMQExtension.getSender(), rabbitMQExtension.getReceiverProvider());
            otherEventBus.start();
        }

        @AfterEach
        void tearDown() {
            otherEventBus.stop();
        }

        @Test
        void eventBusGroupsWithDistinctNamingStrategiesShouldBeIsolated() throws Exception {
            EventCollector listener = new EventCollector();
            EventCollector otherListener = new EventCollector();
            eventBus.register(listener, GROUP_A);
            otherEventBus.register(otherListener, GROUP_B);

            eventBus.dispatch(EVENT, ImmutableSet.of()).block();

            TimeUnit.SECONDS.sleep(1);
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(listener.getEvents()).hasSize(1);
                softly.assertThat(otherListener.getEvents()).isEmpty();
            });
        }

        @Test
        void eventBusPubSubWithDistinctNamingStrategiesShouldBeIsolated() throws Exception {
            EventCollector listener = new EventCollector();
            EventCollector otherListener = new EventCollector();
            eventBus.register(listener, KEY_1);
            otherEventBus.register(otherListener, KEY_1);

            eventBus.dispatch(EVENT, ImmutableSet.of(KEY_1)).block();

            TimeUnit.SECONDS.sleep(1);
            SoftAssertions.assertSoftly(softly -> {
                softly.assertThat(listener.getEvents()).hasSize(1);
                softly.assertThat(otherListener.getEvents()).isEmpty();
            });
        }
    }

    @Nested
    class ErrorGroupDispatchingTest {

        @AfterEach
        void tearDown() {
            rabbitMQExtension.getRabbitMQ().unpause();
        }

        @Test
        void dispatchShouldNotSendToGroupListenerWhenError() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            rabbitMQExtension.getRabbitMQ().pause();

            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());

            assertThat(eventCollector.getEvents()).isEmpty();
        }

        @Test
        void dispatchShouldPersistEventWhenDispatchingNoKeyGetError() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            rabbitMQExtension.getRabbitMQ().pause();

            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());

            assertThat(dispatchingFailureEvents()).containsOnly(EVENT);
        }

        @Test
        void dispatchShouldPersistEventWhenDispatchingWithKeysGetError() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);
            Mono.from(eventBus().register(eventCollector, KEY_1)).block();

            rabbitMQExtension.getRabbitMQ().pause();

            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());

            assertThat(dispatchingFailureEvents()).containsOnly(EVENT);
        }

        @Test
        void dispatchShouldPersistOnlyOneEventWhenDispatchingMultiGroupsGetError() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);
            eventBus().register(eventCollector, GROUP_B);

            rabbitMQExtension.getRabbitMQ().pause();

            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());

            assertThat(dispatchingFailureEvents()).containsOnly(EVENT);
        }

        @Test
        void dispatchShouldPersistEventsWhenDispatchingGroupsGetErrorMultipleTimes() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            rabbitMQExtension.getRabbitMQ().pause();
            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());
            doQuietly(() -> eventBus().dispatch(EVENT_2, NO_KEYS).block());

            assertThat(dispatchingFailureEvents()).containsExactly(EVENT, EVENT_2);
        }

        @Test
        void dispatchShouldPersistEventsWhenDispatchingTheSameEventGetErrorMultipleTimes() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            rabbitMQExtension.getRabbitMQ().pause();
            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());
            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());

            assertThat(dispatchingFailureEvents()).containsExactly(EVENT, EVENT);
        }

        @Test
        void reDeliverShouldDeliverToAllGroupsWhenDispatchingFailure() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            EventCollector eventCollector2 = eventCollector();
            eventBus().register(eventCollector2, GROUP_B);

            rabbitMQExtension.getRabbitMQ().pause();
            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());
            rabbitMQExtension.getRabbitMQ().unpause();
            dispatchingFailureEvents()
                .forEach(event -> eventBus().reDeliver(dispatchingFailureGroup, event).block());

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(eventCollector.getEvents())
                    .hasSameElementsAs(eventCollector2.getEvents())
                    .containsExactly(EVENT));
        }

        @Test
        void reDeliverShouldAddEventInDeadLetterWhenGettingError() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            rabbitMQExtension.getRabbitMQ().pause();
            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());
            getSpeedProfile().longWaitCondition()
                .until(() -> deadLetter().containEvents().block());

            doQuietly(() -> eventBus().reDeliver(dispatchingFailureGroup, EVENT).block());
            rabbitMQExtension.getRabbitMQ().unpause();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(dispatchingFailureEvents())
                    .containsExactly(EVENT, EVENT));
        }

        @Test
        void reDeliverShouldNotStoreEventInAnotherGroupWhenGettingError() {
            EventCollector eventCollector = eventCollector();
            eventBus().register(eventCollector, GROUP_A);

            rabbitMQExtension.getRabbitMQ().pause();
            doQuietly(() -> eventBus().dispatch(EVENT, NO_KEYS).block());
            getSpeedProfile().longWaitCondition()
                .until(() -> deadLetter().containEvents().block());

            doQuietly(() -> eventBus().reDeliver(dispatchingFailureGroup, EVENT).block());
            rabbitMQExtension.getRabbitMQ().unpause();

            getSpeedProfile().shortWaitCondition()
                .untilAsserted(() -> assertThat(deadLetter().groupsWithFailedEvents().toStream())
                    .hasOnlyElementsOfType(DispatchingFailureGroup.class));
        }

        private Stream<Event> dispatchingFailureEvents() {
            return deadLetter().failedIds(dispatchingFailureGroup)
                .flatMap(insertionId -> deadLetter().failedEvent(dispatchingFailureGroup, insertionId))
                .toStream();
        }

        private void doQuietly(Runnable runnable) {
            try {
                runnable.run();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void assertThatListenerReceiveOneEvent(EventListener listener) {
        RabbitMQFixture.awaitAtMostThirtySeconds
            .untilAsserted(() -> verify(listener).event(EVENT));
    }
}
