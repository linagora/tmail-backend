package com.linagora.tmail.webadmin.archival;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InboxArchivalTaskDTO(@JsonProperty("type") String type) implements TaskDTO {
    public static TaskDTOModule<InboxArchivalTask, InboxArchivalTaskDTO> module(InboxArchivalService service) {
        return DTOModule.forDomainObject(InboxArchivalTask.class)
            .convertToDTO(InboxArchivalTaskDTO.class)
            .toDomainObjectConverter(dto -> new InboxArchivalTask(service))
            .toDTOConverter(((domainObject, typeName) -> new InboxArchivalTaskDTO(typeName)))
            .typeName(InboxArchivalTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    public String getType() {
        return type;
    }
}
