package com.linagora.openpaas.james.app;

import java.util.List;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.CommandHandler;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.eventstore.EventStore;
import org.reactivestreams.Publisher;

import reactor.core.publisher.Mono;

public class RegisterStorageStrategyCommandHandler implements CommandHandler<RegisterStorageStrategy> {
    static final String STORAGE_STRATEGY_CONFIGURATION_AGGREGATE_KEY = "BlobStoreStorageStrategyConfiguration";
    public static final AggregateId AGGREGATE_ID = () -> STORAGE_STRATEGY_CONFIGURATION_AGGREGATE_KEY;

    private final EventStore eventStore;

    public RegisterStorageStrategyCommandHandler(EventStore eventStore) {
        this.eventStore = eventStore;
    }

    @Override
    public Class<RegisterStorageStrategy> handledClass() {
        return RegisterStorageStrategy.class;
    }

    @Override
    public Publisher<List<? extends Event>> handle(RegisterStorageStrategy command) {
        return Mono.from(eventStore.getEventsOfAggregate(AGGREGATE_ID))
            .map(history -> StorageStrategyAggregate.load(AGGREGATE_ID, history))
            .map(aggregate -> aggregate.registerStorageStrategy(command));
    }
}
