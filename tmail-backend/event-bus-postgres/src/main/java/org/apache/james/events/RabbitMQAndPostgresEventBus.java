package org.apache.james.events;

import java.util.Collection;
import java.util.Set;

import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.backends.rabbitmq.RabbitMQConfiguration;
import org.apache.james.backends.rabbitmq.ReactorRabbitMQChannelPool;
import org.apache.james.backends.rabbitmq.ReceiverProvider;
import org.apache.james.lifecycle.api.Startable;
import org.apache.james.metrics.api.MetricFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;
import reactor.rabbitmq.Sender;

public class RabbitMQAndPostgresEventBus implements EventBus, Startable {
    private static final Set<RegistrationKey> NO_KEY = ImmutableSet.of();
    private static final String NOT_RUNNING_ERROR_MESSAGE = "Event Bus is not running";
    static final String EVENT_BUS_ID = "eventBusId";

    private final NamingStrategy namingStrategy;
    private final EventSerializer eventSerializer;
    private final RoutingKeyConverter routingKeyConverter;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventBusId eventBusId;
    private final EventDeadLetters eventDeadLetters;
    private final ListenerExecutor listenerExecutor;
    private final Sender sender;
    private final ReceiverProvider receiverProvider;
    private final ReactorRabbitMQChannelPool channelPool;
    private final RabbitMQConfiguration configuration;
    private final MetricFactory metricFactory;
    private final PostgresExecutor postgresExecutor;

    private volatile boolean isRunning;
    private volatile boolean isStopping;
    private GroupRegistrationHandler groupRegistrationHandler;
    private PostgresKeyRegistrationHandler keyRegistrationHandler;
    private TMailEventDispatcher eventDispatcher;

    @Inject
    public RabbitMQAndPostgresEventBus(NamingStrategy namingStrategy, Sender sender, ReceiverProvider receiverProvider, EventSerializer eventSerializer,
                                       RetryBackoffConfiguration retryBackoff,
                                       RoutingKeyConverter routingKeyConverter,
                                       EventDeadLetters eventDeadLetters, MetricFactory metricFactory, ReactorRabbitMQChannelPool channelPool,
                                       EventBusId eventBusId, RabbitMQConfiguration configuration,
                                       PostgresExecutor postgresExecutor) {
        this.namingStrategy = namingStrategy;
        this.sender = sender;
        this.receiverProvider = receiverProvider;
        this.listenerExecutor = new ListenerExecutor(metricFactory);
        this.channelPool = channelPool;
        this.eventBusId = eventBusId;
        this.eventSerializer = eventSerializer;
        this.routingKeyConverter = routingKeyConverter;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.configuration = configuration;
        this.metricFactory = metricFactory;
        this.postgresExecutor = postgresExecutor;
        this.isRunning = false;
        this.isStopping = false;
    }

    public void start() {
        if (!isRunning && !isStopping) {

            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new PostgresKeyRegistrationHandler(namingStrategy, eventBusId, eventSerializer, routingKeyConverter,
                localListenerRegistry, listenerExecutor, retryBackoff, metricFactory, postgresExecutor);
            groupRegistrationHandler = new GroupRegistrationHandler(namingStrategy, eventSerializer, channelPool, sender, receiverProvider, retryBackoff, eventDeadLetters, listenerExecutor, eventBusId, configuration);
            eventDispatcher = new TMailEventDispatcher(namingStrategy, eventBusId, eventSerializer, sender, localListenerRegistry, listenerExecutor, eventDeadLetters, configuration,
                postgresExecutor);

            eventDispatcher.start();
            keyRegistrationHandler.start();
            isRunning = true;
        }
    }

    @VisibleForTesting
    void startWithoutStartingKeyRegistrationHandler() {
        if (!isRunning && !isStopping) {

            LocalListenerRegistry localListenerRegistry = new LocalListenerRegistry();
            keyRegistrationHandler = new PostgresKeyRegistrationHandler(namingStrategy, eventBusId, eventSerializer, routingKeyConverter,
                localListenerRegistry, listenerExecutor, retryBackoff, metricFactory, postgresExecutor);
            groupRegistrationHandler = new GroupRegistrationHandler(namingStrategy, eventSerializer, channelPool, sender, receiverProvider, retryBackoff, eventDeadLetters, listenerExecutor, eventBusId, configuration);
            eventDispatcher = new TMailEventDispatcher(namingStrategy, eventBusId, eventSerializer, sender, localListenerRegistry, listenerExecutor, eventDeadLetters, configuration,
                postgresExecutor);

            keyRegistrationHandler.declarePubSubChannel();

            eventDispatcher.start();
            isRunning = true;
        }
    }

    @VisibleForTesting
    void startKeyRegistrationHandler() {
        keyRegistrationHandler.start();
    }

    @PreDestroy
    public void stop() {
        if (isRunning && !isStopping) {
            isStopping = true;
            isRunning = false;
            groupRegistrationHandler.stop();
            keyRegistrationHandler.stop();
        }
    }

    @Override
    public Mono<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return Mono.from(metricFactory.decoratePublisherWithTimerMetric("postgres-register", keyRegistrationHandler.register(listener, key)));
    }

    @Override
    public Registration register(EventListener.ReactiveEventListener listener, Group group) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        return groupRegistrationHandler.register(listener, group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> key) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        if (!event.isNoop()) {
            return Mono.from(metricFactory.decoratePublisherWithTimerMetric("postgres-dispatch", eventDispatcher.dispatch(event, key)));
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> reDeliver(Group group, Event event) {
        Preconditions.checkState(isRunning, NOT_RUNNING_ERROR_MESSAGE);
        if (!event.isNoop()) {
            /*
            if the eventBus.dispatch() gets error while dispatching an event (rabbitMQ network outage maybe),
            which means all the group consumers will not be receiving that event.

            We store the that event in the dead letter and expecting in the future, it will be dispatched
            again not only for a specific consumer but all.

            That's why it is special, and we need to check event type before processing further.
            */
            if (group instanceof DispatchingFailureGroup) {
                return eventDispatcher.dispatch(event, NO_KEY);
            }
            return groupRegistrationHandler.retrieveGroupRegistration(group).reDeliver(event);
        }
        return Mono.empty();
    }

    @Override
    public EventBusName eventBusName() {
        return namingStrategy.getEventBusName();
    }

    @Override
    public Collection<Group> listRegisteredGroups() {
        return groupRegistrationHandler.registeredGroups();
    }
}