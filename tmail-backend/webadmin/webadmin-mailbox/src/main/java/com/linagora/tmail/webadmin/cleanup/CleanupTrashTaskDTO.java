package com.linagora.tmail.webadmin.cleanup;

import java.util.Optional;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CleanupTrashTaskDTO(@JsonProperty("type") String type, @JsonProperty("runningOptions") Optional<RunningOptionsDTO> runningOptions) implements TaskDTO {

    public static TaskDTOModule<CleanupTrashTask, CleanupTrashTaskDTO> module(CleanupTrashService service) {
        return DTOModule.forDomainObject(CleanupTrashTask.class)
            .convertToDTO(CleanupTrashTaskDTO.class)
            .toDomainObjectConverter(dto -> new CleanupTrashTask(service,
                dto.runningOptions.map(RunningOptionsDTO::asDomainObject).orElse(RunningOptions.DEFAULT)))
            .toDTOConverter(((domainObject, typeName) ->
                new CleanupTrashTaskDTO(typeName, Optional.of(RunningOptionsDTO.asDTO(domainObject.getRunningOptions())))))
            .typeName(CleanupTrashTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
