package com.linagora.openpaas.james.app;

import javax.inject.Inject;

import org.apache.james.eventsourcing.EventSourcingSystem;
import org.apache.james.eventsourcing.Subscriber;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.apache.james.server.blob.deduplication.StorageStrategy;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Mono;

public class EventsourcingStorageStrategy {

    private static final ImmutableSet<Subscriber> NO_SUBSCRIBER = ImmutableSet.of();

    private final EventSourcingSystem eventSourcingSystem;

    @Inject
    public EventsourcingStorageStrategy(EventStore eventStore) {
        this.eventSourcingSystem = EventSourcingSystem.fromJava(
            ImmutableSet.of(new RegisterStorageStrategyCommandHandler(eventStore)),
            NO_SUBSCRIBER,
            eventStore);
    }

    public void registerStorageStrategy(StorageStrategy newStorageStrategy) {
        Preconditions.checkNotNull(newStorageStrategy);

        Mono.from(eventSourcingSystem.dispatch(new RegisterStorageStrategy(newStorageStrategy))).block();
    }
}
