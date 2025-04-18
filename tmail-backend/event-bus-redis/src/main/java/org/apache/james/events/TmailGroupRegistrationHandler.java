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
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.REQUEUE;
import static org.apache.james.backends.rabbitmq.Constants.evaluateAutoDelete;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;
import static org.apache.james.backends.rabbitmq.Constants.evaluateExclusive;
import static org.apache.james.events.GroupRegistration.DEFAULT_RETRY_COUNT;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.util.ReactorUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.AcknowledgableDelivery;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ConsumeOptions;
import reactor.rabbitmq.QueueSpecification;
import reactor.rabbitmq.Receiver;
import reactor.rabbitmq.Sender;
import reactor.util.retry.Retry;

public class TmailGroupRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(TmailGroupRegistrationHandler.class);

    public static class GroupRegistrationHandlerGroup extends Group {

    }

    public static final Group GROUP = new GroupRegistrationHandlerGroup();

    private final NamingStrategy namingStrategy;
    private final Map<Group, GroupRegistration> groupRegistrations;
    private final EventSerializer eventSerializer;
    private final ReactorRabbitMQChannelPool channelPool;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventDeadLetters eventDeadLetters;
    private final ListenerExecutor listenerExecutor;
    private final RabbitMQConfiguration configuration;
    private final GroupRegistration.WorkQueueName queueName;
    private final Scheduler scheduler;
    private Optional<Disposable> consumer;

    TmailGroupRegistrationHandler(NamingStrategy namingStrategy, EventSerializer eventSerializer, ReactorRabbitMQChannelPool channelPool, Sender sender, ReceiverProvider receiverProvider,
                                  RetryBackoffConfiguration retryBackoff,
                                  EventDeadLetters eventDeadLetters, ListenerExecutor listenerExecutor, EventBusId eventBusId, RabbitMQConfiguration configuration) {
        this.namingStrategy = namingStrategy;
        this.eventSerializer = eventSerializer;
        this.channelPool = channelPool;
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.listenerExecutor = listenerExecutor;
        this.configuration = configuration;
        this.groupRegistrations = new ConcurrentHashMap<>();
        this.queueName = namingStrategy.workQueue(GROUP);
        this.scheduler = Schedulers.newBoundedElastic(EventBus.EXECUTION_RATE, ReactorUtils.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "groups-handler");
        this.consumer = Optional.empty();
    }

    Stream<GroupRegistration> synchronousGroupRegistrations() {
        return groupRegistrations.entrySet()
            .stream()
            .filter(registration -> RabbitMQAndRedisEventBus.shouldBeExecutedSynchronously(registration.getKey()))
            .map(Map.Entry::getValue);
    }

    Stream<GroupRegistration> asynchronousGroupRegistrations() {
        return groupRegistrations.entrySet()
            .stream()
            .filter(registration -> !RabbitMQAndRedisEventBus.shouldBeExecutedSynchronously(registration.getKey()))
            .map(Map.Entry::getValue);
    }

    GroupRegistration retrieveGroupRegistration(Group group) {
        return Optional.ofNullable(groupRegistrations.get(group))
            .orElseThrow(() -> new GroupRegistrationNotFound(group));
    }

    public void start() {
        channelPool.createWorkQueue(
            QueueSpecification.queue(queueName.asString())
                .durable(evaluateDurable(DURABLE, configuration.isQuorumQueuesUsed()))
                .exclusive(evaluateExclusive(!EXCLUSIVE, configuration.isQuorumQueuesUsed()))
                .autoDelete(evaluateAutoDelete(!AUTO_DELETE, configuration.isQuorumQueuesUsed()))
                .arguments(configuration.workQueueArgumentsBuilder()
                    .deadLetter(namingStrategy.deadLetterExchange())
                    .build()),
            BindingSpecification.binding()
                .exchange(namingStrategy.exchange())
                .queue(queueName.asString())
                .routingKey(EMPTY_ROUTING_KEY))
            .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.boundedElastic()))
            .block();

        this.consumer = Optional.of(consumeWorkQueue());
    }

    private Disposable consumeWorkQueue() {
        return Flux.using(
                receiverProvider::createReceiver,
            receiver -> receiver.consumeManualAck(queueName.asString(), new ConsumeOptions().qos(EventBus.EXECUTION_RATE)),
            Receiver::close)
            .filter(delivery -> Objects.nonNull(delivery.getBody()))
            .flatMap(this::deliver, EventBus.EXECUTION_RATE)
            .subscribeOn(scheduler)
            .subscribe();
    }

    private Mono<Void> deliver(AcknowledgableDelivery acknowledgableDelivery) {
        byte[] eventAsBytes = acknowledgableDelivery.getBody();

        return deserializeEvents(eventAsBytes)
            .flatMapIterable(events -> asynchronousGroupRegistrations()
                .map(group -> Pair.of(group, events))
                .collect(ImmutableList.toImmutableList()))
            .flatMap(event -> event.getLeft().runListenerReliably(DEFAULT_RETRY_COUNT, event.getRight()))
            .then(Mono.<Void>fromRunnable(acknowledgableDelivery::ack).subscribeOn(Schedulers.boundedElastic()))
            .then()
            .onErrorResume(e -> {
                LOGGER.error("Unable to process delivery for group {}", GROUP, e);
                return Mono.fromRunnable(() -> acknowledgableDelivery.nack(!REQUEUE))
                    .subscribeOn(Schedulers.boundedElastic())
                    .then();
            });
    }

    private Mono<List<Event>> deserializeEvents(byte[] eventAsBytes) {
        return Mono.fromCallable(() -> eventSerializer.asEventsFromBytes(eventAsBytes));
    }

    void stop() {
        groupRegistrations.values().forEach(groupRegistration -> Mono.from(groupRegistration.unregister()).block());
        consumer.ifPresent(Disposable::dispose);
        Optional.ofNullable(scheduler).ifPresent(Scheduler::dispose);
    }

    void restart() {
        Optional<Disposable> previousConsumer = consumer;
        consumer = Optional.of(consumeWorkQueue());
        previousConsumer
            .filter(Predicate.not(Disposable::isDisposed))
            .ifPresent(Disposable::dispose);

        groupRegistrations.values()
            .forEach(GroupRegistration::restart);
    }

    Registration register(EventListener.ReactiveEventListener listener, Group group) {
        if (groupRegistrations.isEmpty()) {
            start();
        }
        return groupRegistrations
            .compute(group, (groupToRegister, oldGroupRegistration) -> {
                if (oldGroupRegistration != null) {
                    throw new GroupAlreadyRegistered(group);
                }
                return newGroupRegistration(listener, groupToRegister);
            })
            .start();
    }

    private GroupRegistration newGroupRegistration(EventListener.ReactiveEventListener listener, Group group) {
        return new GroupRegistration(
            namingStrategy, channelPool, sender,
            receiverProvider,
            eventSerializer,
            listener,
            group,
            retryBackoff,
            eventDeadLetters,
            () -> groupRegistrations.remove(group),
            listenerExecutor, configuration);
    }

    Collection<Group> registeredGroups() {
        return groupRegistrations.keySet();
    }
}