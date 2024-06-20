package org.apache.james.events;

import static org.apache.james.events.KeyChannelMessage.quoteChannel;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;
import org.apache.james.util.MDCStructuredLogger;
import org.apache.james.util.ReactorUtils;
import org.apache.james.util.StructuredLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import io.r2dbc.postgresql.api.Notification;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.Wrapped;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

class PostgresKeyRegistrationHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresKeyRegistrationHandler.class);
    private static final Duration TOPOLOGY_CHANGES_TIMEOUT = Duration.ofMinutes(1);

    private final EventBusId eventBusId;
    private final LocalListenerRegistry localListenerRegistry;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RegistrationQueueName registrationChannel;
    private final PostgresKeyRegistrationBinder registrationBinder;
    private final ListenerExecutor listenerExecutor;
    private final RetryBackoffConfiguration retryBackoff;
    private Optional<Disposable> receiverSubscriber;
    private final MetricFactory metricFactory;
    private final PostgresExecutor postgresExecutor;
    private Scheduler scheduler;

    PostgresKeyRegistrationHandler(NamingStrategy namingStrategy, EventBusId eventBusId, EventSerializer eventSerializer,
                                   RoutingKeyConverter routingKeyConverter, LocalListenerRegistry localListenerRegistry,
                                   ListenerExecutor listenerExecutor, RetryBackoffConfiguration retryBackoff, MetricFactory metricFactory,
                                   PostgresExecutor postgresExecutor) {
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.localListenerRegistry = localListenerRegistry;
        this.listenerExecutor = listenerExecutor;
        this.retryBackoff = retryBackoff;
        this.metricFactory = metricFactory;
        this.registrationChannel = namingStrategy.queueName(eventBusId);
        this.registrationBinder = new PostgresKeyRegistrationBinder(postgresExecutor, registrationChannel);
        this.receiverSubscriber = Optional.empty();
        this.postgresExecutor = postgresExecutor;
    }

    void start() {
        scheduler = Schedulers.newBoundedElastic(EventBus.EXECUTION_RATE, ReactorUtils.DEFAULT_BOUNDED_ELASTIC_QUEUESIZE, "keys-handler"); // worker thread pool for each key queue

        declarePubSubChannel();

        Disposable newSubscription = Flux.usingWhen(postgresExecutor.connectionFactory().getConnection(Optional.empty()),
                this::listenForNotifications,
                connection -> postgresExecutor.connectionFactory().closeConnection(connection))
            .flatMap(this::handleChannelMessage, EventBus.EXECUTION_RATE)
            .doOnError(throwable -> LOGGER.error(throwable.getMessage()))
            .subscribeOn(scheduler)
            .subscribe();

        receiverSubscriber = Optional.of(newSubscription);
    }

    private Flux<Notification> listenForNotifications(Connection connection) {
        String quotedChannel = quoteChannel(registrationChannel.asString());
        if (connection instanceof Wrapped) {
            // unwrap pool connection
            PostgresqlConnection postgresqlConnection = ((Wrapped<PostgresqlConnection>) connection).unwrap();

            return postgresqlConnection.createStatement("LISTEN " + quotedChannel)
                .execute()
                .thenMany(postgresqlConnection.getNotifications());
        } else {
            PostgresqlConnection postgresqlConnection = (PostgresqlConnection) connection;

            return postgresqlConnection.createStatement("LISTEN " + quotedChannel)
                .execute()
                .thenMany(postgresqlConnection.getNotifications());
        }
    }

    void declarePubSubChannel() {
        // Postgres Notify/Listen channel only dynamically declares upon subscribe/publish. No need to declare it ahead of time.
    }

    void stop() {
        // Postgres Channels are ephemeral and automatically be cleaned when they have no more subscribers.
        receiverSubscriber.filter(Predicate.not(Disposable::isDisposed))
            .ifPresent(Disposable::dispose);
        Optional.ofNullable(scheduler).ifPresent(Scheduler::dispose);
    }

    Mono<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key) {
        LocalListenerRegistry.LocalRegistration registration = localListenerRegistry.addListener(key, listener);

        return registerIfNeeded(key, registration)
            .thenReturn(new KeyRegistration(() -> {
                if (registration.unregister().lastListenerRemoved()) {
                    return Mono.from(metricFactory.decoratePublisherWithTimerMetric("postgres-unregister", registrationBinder.unbind(key)
                        .doOnError(throwable -> LOGGER.error(throwable.getMessage()))
                        .timeout(TOPOLOGY_CHANGES_TIMEOUT)
                        .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.boundedElastic()))));
                }
                return Mono.empty();
            }));
    }

    private Mono<Void> registerIfNeeded(RegistrationKey key, LocalListenerRegistry.LocalRegistration registration) {
        if (registration.isFirstListener()) {
            return registrationBinder.bind(key)
                .doOnError(throwable -> LOGGER.error(throwable.getMessage()))
                .timeout(TOPOLOGY_CHANGES_TIMEOUT)
                .retryWhen(Retry.backoff(retryBackoff.getMaxRetries(), retryBackoff.getFirstBackoff()).jitter(retryBackoff.getJitterFactor()).scheduler(Schedulers.boundedElastic()));
        }
        return Mono.empty();
    }

    private Mono<Void> handleChannelMessage(Notification notification) {
        // the following log should be removed once we finished monitoring Postgres event bus keys implementation
        LOGGER.info("Processing message body {} from Postgres channel {}", notification.getParameter(), notification.getName());

        if (notification.getParameter() == null) {
            return Mono.empty();
        }

        KeyChannelMessage keyChannelMessage = KeyChannelMessage.parse(notification.getParameter());
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
