package com.linagora.openpaas.james.app;

import org.apache.james.eventsourcing.AggregateId;
import org.apache.james.eventsourcing.Event;
import org.apache.james.eventsourcing.EventId;
import org.apache.james.server.blob.deduplication.StorageStrategy;

public class StorageStrategyChanged implements Event {
    private final EventId eventId;
    private final AggregateId aggregateId;
    private final StorageStrategy storageStrategy;

    public StorageStrategyChanged(EventId eventId, AggregateId aggregateId, StorageStrategy storageStrategy) {
        this.eventId = eventId;
        this.aggregateId = aggregateId;
        this.storageStrategy = storageStrategy;
    }

    @Override
    public EventId eventId() {
        return eventId;
    }

    @Override
    public AggregateId getAggregateId() {
        return aggregateId;
    }

    public StorageStrategy getStorageStrategy() {
        return storageStrategy;
    }
}
