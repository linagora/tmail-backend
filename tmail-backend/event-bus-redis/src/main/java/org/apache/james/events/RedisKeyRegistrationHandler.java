package org.apache.james.events;

import static org.apache.james.events.TMailEventDispatcher.REDIS_ERROR_PREDICATE;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.lettuce.core.api.reactive.RedisSetReactiveCommands;
import io.lettuce.core.pubsub.api.reactive.ChannelMessage;
import io.lettuce.core.pubsub.api.reactive.RedisPubSubReactiveCommands;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

class RedisKeyRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisKeyRegistrationHandler.class);

    private final EventBusId eventBusId;
    private final LocalListenerRegistry localListenerRegistry;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RegistrationQueueName registrationChannel;
    private final RedisKeyRegistrationBinder registrationBinder;
    private final ListenerExecutor listenerExecutor;
    private final RetryBackoffConfiguration retryBackoff;
    private Optional<Disposable> receiverSubscriber;
    private final MetricFactory metricFactory;
    private final RedisPubSubReactiveCommands<String, String> redisSubscriber;
    private Scheduler scheduler;
    private final RedisEventBusConfiguration redisEventBusConfiguration;
    private volatile boolean isStopping;

    RedisKeyRegistrationHandler(NamingStrategy namingStrategy, EventBusId eventBusId, EventSerializer eventSerializer,
                                RoutingKeyConverter routingKeyConverter, LocalListenerRegistry localListenerRegistry,
                                ListenerExecutor listenerExecutor, RetryBackoffConfiguration retryBackoff, MetricFactory metricFactory,
                                RedisEventBusClientFactory redisEventBusClientFactory,
                                RedisSetReactiveCommands<String, String> redisSetReactiveCommands, RedisEventBusConfiguration redisEventBusConfiguration) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.localListenerRegistry = localListenerRegistry;
        this.listenerExecutor = listenerExecutor;
        this.retryBackoff = retryBackoff;
        this.metricFactory = metricFactory;
        this.redisEventBusConfiguration = redisEventBusConfiguration;
        this.registrationChannel = namingStrategy.queueName(eventBusId);
        this.registrationBinder = new RedisKeyRegistrationBinder(redisSetReactiveCommands, registrationChannel);
        this.receiverSubscriber = Optional.empty();
        this.redisSubscriber = redisEventBusClientFactory.createRedisPubSubCommand();
        this.isStopping = false;
    }

    void start() {
        scheduler = Schedulers.newBoundedElastic(EventBus.EXECUTION_RATE, ReactorUtils.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "keys-handler"); // worker thread pool for each key queue

        declarePubSubChannel();

        Disposable newSubscription = Mono.from(redisSubscriber.subscribe(registrationChannel.asString()))
            .thenMany(redisSubscriber.observeChannels())
            .flatMap(this::handleChannelMessage, EventBus.EXECUTION_RATE)
            .doOnError(throwable -> LOGGER.error(throwable.getMessage()))
            .subscribeOn(scheduler)
            .subscribe();

        receiverSubscriber = Optional.of(newSubscription);
    }

    void declarePubSubChannel() {
        // Pub/sub channel only dynamically declares upon subscribe/publish. No need to declare it ahead of time.
    }

    void stop() {
        isStopping = true;

        // delete the Pub/Sub channel: Redis Channels are ephemeral and automatically expire when they have no more subscribers.
        redisSubscriber.unsubscribe(registrationChannel.asString())
            .timeout(redisEventBusConfiguration.durationTimeout())
            .onErrorResume(REDIS_ERROR_PREDICATE.and(e -> redisEventBusConfiguration.failureIgnore()), e -> {
                LOGGER.warn("Error while unsubscribing from channel", e);
                return Mono.empty();
            })
            .block();
        receiverSubscriber.filter(Predicate.not(Disposable::isDisposed))
                .ifPresent(Disposable::dispose);
        Optional.ofNullable(scheduler).ifPresent(Scheduler::dispose);
    }

    Mono<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key) {
        // RabbitMQ impl: register the binding mapping registration key (routing key) -> target queue on RabbitMQ
        // Redis impl: there is no routing key concept in Redis Pub/Sub (and Redis Streams).
        // Solution: store binding registration key - target channel mapping in Redis under key-value. Upon dispatching event, check the mapping to know the target publish channel.

        LocalListenerRegistry.LocalRegistration registration = localListenerRegistry.addListener(key, listener);

        return registerIfNeeded(key, registration)
            .thenReturn(new KeyRegistration(() -> {
                if (registration.unregister().lastListenerRemoved()) {
                    return Mono.from(metricFactory.decoratePublisherWithTimerMetric("redis-unregister", registrationBinder.unbind(key)
                        .doOnError(any -> !isStopping, throwable -> LOGGER.error("Error while unbinding key", throwable))
                        .timeout(redisEventBusConfiguration.durationTimeout())
                        .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff())
                            .jitter(retryBackoff.getJitterFactor())
                            .scheduler(Schedulers.boundedElastic()))
                        .onErrorResume(error -> (REDIS_ERROR_PREDICATE.test(error.getCause()) && redisEventBusConfiguration.failureIgnore()), error -> {
                            LOGGER.warn("Error while unbinding key", error);
                            return Mono.empty();
                        })));
                }
                return Mono.empty();
            }));
    }

    private Mono<Void> registerIfNeeded(RegistrationKey key, LocalListenerRegistry.LocalRegistration registration) {
        if (registration.isFirstListener()) {
            return registrationBinder.bind(key)
                .doOnError(any -> !isStopping, throwable -> LOGGER.error("Error while binding key", throwable))
                .timeout(redisEventBusConfiguration.durationTimeout())
                .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.boundedElastic()))
                .onErrorResume(error -> (REDIS_ERROR_PREDICATE.test(error.getCause()) && redisEventBusConfiguration.failureIgnore()), error -> {
                    LOGGER.warn("Error while binding key", error);
                    return Mono.empty();
                });
        }
        return Mono.empty();
    }

    private Mono<Void> handleChannelMessage(ChannelMessage<String, String> channelMessage) {
        // the following log should be removed once we finished monitoring Redis event bus keys implementation
        LOGGER.info("Processing message body {} from Redis channel {}", channelMessage.getMessage(), channelMessage.getChannel());

        if (channelMessage.getMessage() == null) {
            return Mono.empty();
        }

        KeyChannelMessage keyChannelMessage = KeyChannelMessage.parse(channelMessage.getMessage());
        RegistrationKey registrationKey = routingKeyConverter.toRegistrationKey(keyChannelMessage.routingKey());

        List<EventListener.ReactiveEventListener> listenersToCall = localListenerRegistry.getLocalListeners(registrationKey)
            .stream()
            .filter(listener -> !isLocalSynchronousListeners(keyChannelMessage.eventBusId(), listener))
            .collect(ImmutableList.toImmutableList());

        if (listenersToCall.isEmpty()) {
            return Mono.empty();
        }

        Event event = toEvent(keyChannelMessage.eventAsJson());

        return Flux.fromIterable(listenersToCall)
            .flatMap(listener -> executeListener(listener, event, registrationKey), EventBus.EXECUTION_RATE)
            .then();
    }

    private Mono<Void> executeListener(EventListener.ReactiveEventListener listener, Event event, RegistrationKey key) {
        MDCBuilder mdcBuilder = MDCBuilder.create()
            .addToContext(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key.asString());

        return listenerExecutor.execute(listener, mdcBuilder, event)
            .doOnError(e -> structuredLogger(event, key)
                .log(logger -> logger.error("Exception happens when handling event", e)))
            .onErrorResume(e -> Mono.empty())
            .then();
    }

    private boolean isLocalSynchronousListeners(EventBusId eventBusId, EventListener listener) {
        return eventBusId.equals(this.eventBusId) &&
            listener.getExecutionMode().equals(EventListener.ExecutionMode.SYNCHRONOUS);
    }

    private Event toEvent(String eventAsJson) {
        return eventSerializer.asEvent(eventAsJson);
    }

    private StructuredLogger structuredLogger(Event event, RegistrationKey key) {
        return MDCStructuredLogger.forLogger(LOGGER)
            .field(EventBus.StructuredLoggingFields.EVENT_ID, event.getEventId().getId().toString())
            .field(EventBus.StructuredLoggingFields.EVENT_CLASS, event.getClass().getCanonicalName())
            .field(EventBus.StructuredLoggingFields.USER, event.getUsername().asString())
            .field(EventBus.StructuredLoggingFields.REGISTRATION_KEY, key.asString());
    }
}
