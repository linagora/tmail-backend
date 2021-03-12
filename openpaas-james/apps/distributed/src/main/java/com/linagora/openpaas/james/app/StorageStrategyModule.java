package com.linagora.openpaas.james.app;

import org.apache.james.eventsourcing.eventstore.cassandra.dto.EventDTOModule;

public interface StorageStrategyModule {

    String TYPE_NAME = "storage-strategy-changed";

    EventDTOModule<StorageStrategyChanged, StorageStrategyChangedDTO> STORAGE_STRATEGY =
        EventDTOModule
            .forEvent(StorageStrategyChanged.class)
            .convertToDTO(StorageStrategyChangedDTO.class)
            .toDomainObjectConverter(StorageStrategyChangedDTO::toEvent)
            .toDTOConverter(StorageStrategyChangedDTO::from)
            .typeName(TYPE_NAME)
            .withFactory(EventDTOModule::new);
}