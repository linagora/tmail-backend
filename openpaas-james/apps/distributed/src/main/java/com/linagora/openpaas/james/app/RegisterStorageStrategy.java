package com.linagora.openpaas.james.app;

import org.apache.james.eventsourcing.Command;
import org.apache.james.server.blob.deduplication.StorageStrategy;

public class RegisterStorageStrategy implements Command {
    private final StorageStrategy storageStrategy;

    public RegisterStorageStrategy(StorageStrategy storageStrategy) {
        this.storageStrategy = storageStrategy;
    }

    public StorageStrategy getStorageStrategy() {
        return storageStrategy;
    }
}
