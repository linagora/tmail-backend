package com.linagora.tmail.webadmin.cleanup;

import java.time.Instant;
import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableList;

public record CleanupTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                  @JsonProperty("processedUsersCount") long processedUsersCount,
                                                  @JsonProperty("deletedMessagesCount") long deletedMessagesCount,
                                                  @JsonProperty("failedUsers") ImmutableList<String> failedUsers,
                                                  @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions,
                                                  @JsonProperty("timestamp") Instant timestamp) implements AdditionalInformationDTO {

    public static AdditionalInformationDTOModule<CleanupTaskDetails, CleanupTaskAdditionalInformationDTO> cleanupTrashModule() {
        return DTOModule.forDomainObject(CleanupTaskDetails.class)
            .convertToDTO(CleanupTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(CleanupTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(CleanupTaskAdditionalInformationDTO::fromDomainObject)
            .typeName(CleanupTrashTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);
    }

    private static CleanupTaskAdditionalInformationDTO fromDomainObject(CleanupTaskDetails details, String type) {
        return new CleanupTaskAdditionalInformationDTO(type,
            details.processedUsersCount(),
            details.deletedMessagesCount(),
            details.failedUsers(),
            Optional.of(RunningOptionsDTO.asDTO(details.runningOptions())),
            details.timestamp());
    }

    private CleanupTaskDetails toDomainObject() {
        return new CleanupTaskDetails(timestamp,
            processedUsersCount,
            deletedMessagesCount,
            failedUsers,
            runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT));
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }
}
