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

import static com.rabbitmq.client.MessageProperties.PERSISTENT_TEXT_PLAIN;
import static org.apache.james.backends.rabbitmq.Constants.AUTO_DELETE;
import static org.apache.james.backends.rabbitmq.Constants.DIRECT_EXCHANGE;
import static org.apache.james.backends.rabbitmq.Constants.DURABLE;
import static org.apache.james.backends.rabbitmq.Constants.EMPTY_ROUTING_KEY;
import static org.apache.james.backends.rabbitmq.Constants.EXCLUSIVE;
import static org.apache.james.backends.rabbitmq.Constants.evaluateAutoDelete;
import static org.apache.james.backends.rabbitmq.Constants.evaluateDurable;
import static org.apache.james.backends.rabbitmq.Constants.evaluateExclusive;
import static org.apache.james.events.GroupRegistration.DEFAULT_RETRY_COUNT;
import static org.apache.james.events.RabbitMQAndRedisEventBus.EVENT_BUS_ID;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.events.RoutingKeyConverter.RoutingKey;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.rabbitmq.client.AMQP;

import io.lettuce.core.RedisException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.rabbitmq.BindingSpecification;
import reactor.rabbitmq.ExchangeSpecification;
import reactor.rabbitmq.OutboundMessage;
import reactor.rabbitmq.Sender;
import reactor.util.function.Tuples;
import reactor.util.retry.Retry;

public class TMailEventDispatcher {
    public static final Predicate<? super Throwable> REDIS_ERROR_PREDICATE = throwable -> throwable instanceof RedisException || throwable instanceof TimeoutException;
    private static final Logger LOGGER = LoggerFactory.getLogger(TMailEventDispatcher.class);

    private final NamingStrategy namingStrategy;
    private final EventSerializer eventSerializer;
    private final Sender sender;
    private final LocalListenerRegistry localListenerRegistry;
    private final AMQP.BasicProperties basicProperties;
    private final ListenerExecutor listenerExecutor;
    private final EventDeadLetters deadLetters;
    private final RabbitMQConfiguration configuration;
    private final DispatchingFailureGroup dispatchingFailureGroup;
    private final TmailGroupRegistrationHandler groupRegistrationHandler;
    private final RedisKeyEventDispatcher keyEventDispatcher;

    TMailEventDispatcher(NamingStrategy namingStrategy, EventBusId eventBusId, EventSerializer eventSerializer, Sender sender,
                         LocalListenerRegistry localListenerRegistry,
                         ListenerExecutor listenerExecutor,
                         EventDeadLetters deadLetters, RabbitMQConfiguration configuration,
                         TmailGroupRegistrationHandler groupRegistrationHandler,
                         RedisKeyEventDispatcher keyEventDispatcher) {
        this.namingStrategy = namingStrategy;
        this.eventSerializer = eventSerializer;
        this.sender = sender;
        this.localListenerRegistry = localListenerRegistry;
        this.basicProperties = new AMQP.BasicProperties.Builder()
            .headers(ImmutableMap.of(EVENT_BUS_ID, eventBusId.asString()))
            .deliveryMode(PERSISTENT_TEXT_PLAIN.getDeliveryMode())
            .priority(PERSISTENT_TEXT_PLAIN.getPriority())
            .contentType(PERSISTENT_TEXT_PLAIN.getContentType())
            .build();
        this.listenerExecutor = listenerExecutor;
        this.deadLetters = deadLetters;
        this.configuration = configuration;
        this.dispatchingFailureGroup = new DispatchingFailureGroup(namingStrategy.getEventBusName());
        this.groupRegistrationHandler = groupRegistrationHandler;
        this.keyEventDispatcher = keyEventDispatcher;
    }

    void start() {
        Flux.concat(
            sender.declareExchange(ExchangeSpecification.exchange(namingStrategy.exchange())
                .durable(DURABLE)
                .type(DIRECT_EXCHANGE)),
            sender.declareExchange(ExchangeSpecification.exchange(namingStrategy.deadLetterExchange())
                .durable(DURABLE)
                .type(DIRECT_EXCHANGE)),
            sender.declareQueue(namingStrategy.deadLetterQueue()
                .durable(evaluateDurable(DURABLE, configuration.isQuorumQueuesUsed()))
                .exclusive(evaluateExclusive(!EXCLUSIVE, configuration.isQuorumQueuesUsed()))
                .autoDelete(evaluateAutoDelete(!AUTO_DELETE, configuration.isQuorumQueuesUsed()))
                .arguments(configuration.workQueueArgumentsBuilder()
                    .build())),
            sender.bind(BindingSpecification.binding()
                .exchange(namingStrategy.deadLetterExchange())
                .queue(namingStrategy.deadLetterQueue().getName())
                .routingKey(EMPTY_ROUTING_KEY)))
            .then()
            .block();
    }

    Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        return Flux
            .concat(
                executeLocalSynchronousListeners(ImmutableList.of(new EventBus.EventWithRegistrationKey(event, keys))),
                dispatchToLocalListeners(event, keys),
                dispatchToRemoteListeners(event, keys))
            .doOnError(throwable -> LOGGER.error("error while dispatching event", throwable))
            .then();
    }

    Mono<Void> dispatch(Collection<EventBus.EventWithRegistrationKey> events) {
        return Flux
            .concat(
                executeLocalSynchronousListeners(events),
                dispatchToLocalListeners(events),
                dispatchToRemoteListeners(events))
            .doOnError(throwable -> LOGGER.error("error while dispatching event", throwable))
            .then();
    }

    private Mono<Void> executeLocalSynchronousListeners(Collection<EventBus.EventWithRegistrationKey> events) {
        if (RabbitMQAndRedisEventBus.listenersToExecuteSynchronously.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromStream(groupRegistrationHandler.synchronousGroupRegistrations())
            .flatMap(registration -> registration.runListenerReliably(DEFAULT_RETRY_COUNT, events.stream()
                .map(EventBus.EventWithRegistrationKey::event)
                .collect(ImmutableList.toImmutableList())))
            .then();
    }

    private Mono<Void> dispatchToLocalListeners(Collection<EventBus.EventWithRegistrationKey> events) {
        return Flux.fromIterable(events)
            .concatMap(e -> dispatchToLocalListeners(e.event(), e.keys()))
            .then();
    }

    private Mono<Void> dispatchToLocalListeners(Event event, Set<RegistrationKey> keys) {
        return Flux.fromIterable(keys)
            .flatMap(key -> Flux.fromIterable(localListenerRegistry.getLocalListeners(key))
                .map(listener -> Tuples.of(key, listener)), EventBus.EXECUTION_RATE)
            .filter(pair -> pair.getT2().getExecutionMode() == EventListener.ExecutionMode.SYNCHRONOUS)
            .flatMap(pair -> executeListener(event, pair.getT2(), pair.getT1()), EventBus.EXECUTION_RATE)
            .then();
    }

    private Mono<Void> dispatchToRemoteListeners(Collection<EventBus.EventWithRegistrationKey> events) {
        ImmutableList<Event> underlyingEvents = events.stream()
            .map(EventBus.EventWithRegistrationKey::event)
            .collect(ImmutableList.toImmutableList());

        ImmutableSet<RegistrationKey> keys = events.stream()
            .flatMap(event -> event.keys().stream())
            .collect(ImmutableSet.toImmutableSet());

        return Mono.fromCallable(() -> eventSerializer.toJson(underlyingEvents))
            .flatMap(serializedEvent -> Mono.zipDelayError(
                remoteGroupsDispatch(serializedEvent.getBytes(StandardCharsets.UTF_8), underlyingEvents),
                keyEventDispatcher.remoteKeysDispatch(serializedEvent, keys)))
            .then();
    }

    private Mono<Void> executeListener(Event event, EventListener.ReactiveEventListener listener, RegistrationKey registrationKey) {
        return listenerExecutor.execute(listener,
                    MDCBuilder.create()
                        .addToContext(EventBus.StructuredLoggingFields.REGISTRATION_KEY, registrationKey.asString()),
                    event)
            .onErrorResume(e -> {
                structuredLogger(event, ImmutableSet.of(registrationKey))
                    .log(logger -> logger.error("Exception happens when dispatching event", e));
                return Mono.empty();
            });
    }

    private StructuredLogger structuredLogger(Event event, Set<RegistrationKey> keys) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .field(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .field(EventBus.StructuredLoggingFields.REGISTRATION_KEYS, keys.toString());
    }

    private Mono<Void> dispatchToRemoteListeners(Event event, Set<RegistrationKey> keys) {
        return Mono.fromCallable(() -> eventSerializer.toJson(event))
            .flatMap(serializedEvent -> Mono.zipDelayError(
                remoteGroupsDispatch(serializedEvent.getBytes(StandardCharsets.UTF_8), event),
                keyEventDispatcher.remoteKeysDispatch(serializedEvent, keys)))
            .then();
    }

    private Mono<Void> remoteGroupsDispatch(byte[] serializedEvent, Event event) {
        return remoteDispatchWithAcks(serializedEvent)
            .doOnError(ex -> LOGGER.error(
                "cannot dispatch event of type '{}' belonging '{}' with id '{}' to remote groups, store it into dead letter",
                event.getClass().getSimpleName(),
                event.getUsername().asString(),
                event.getEventId().getId(),
                ex))
            .onErrorResume(ex -> deadLetters.store(dispatchingFailureGroup, event)
                .then(propagateErrorIfNeeded(ex)));
    }

    private Mono<Void> remoteGroupsDispatch(byte[] serializedEvent, List<Event> events) {
        return remoteDispatchWithAcks(serializedEvent)
            .onErrorResume(ex -> Flux.fromIterable(events)
                .map(event -> {
                    LOGGER.error(
                        "cannot dispatch event of type '{}' belonging '{}' with id '{}' to remote groups, store it into dead letter",
                        event.getClass().getSimpleName(),
                        event.getUsername().asString(),
                        event.getEventId().getId(),
                        ex);
                    return deadLetters.store(dispatchingFailureGroup, event);
                })
                .then(propagateErrorIfNeeded(ex)));
    }

    private Mono<Void> propagateErrorIfNeeded(Throwable throwable) {
        if (configuration.eventBusPropagateDispatchError()) {
            return Mono.error(throwable);
        }
        return Mono.empty();
    }

    private Mono<Void> remoteDispatchWithAcks(byte[] serializedEvent) {
        if (configuration.isEventBusPublishConfirmEnabled()) {
            return Mono.from(sender.sendWithPublishConfirms(Mono.just(toMessage(serializedEvent, RoutingKey.empty())))
                .subscribeOn(Schedulers.boundedElastic())) // channel.confirmSelect is synchronous
                .filter(outboundMessageResult -> !outboundMessageResult.isAck())
                .handle((result, sink) -> sink.error(new Exception("Publish was not acked")))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(100)))
                .then();
        } else {
            return sender.send(Mono.just(toMessage(serializedEvent, RoutingKey.empty())));
        }
    }

    private OutboundMessage toMessage(byte[] serializedEvent, RoutingKey routingKey) {
        return new OutboundMessage(namingStrategy.exchange(), routingKey.asString(), basicProperties, serializedEvent);
    }
}
