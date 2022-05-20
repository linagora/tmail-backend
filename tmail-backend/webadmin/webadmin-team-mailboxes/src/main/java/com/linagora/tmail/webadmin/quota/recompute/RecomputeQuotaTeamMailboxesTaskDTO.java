package com.linagora.tmail.webadmin.quota.recompute;

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RecomputeQuotaTeamMailboxesTaskDTO(@JsonProperty("type") String type,
                                                 @JsonProperty("domain") String domain) implements TaskDTO {
    public static TaskDTOModule<RecomputeQuotaTeamMailboxesTask, RecomputeQuotaTeamMailboxesTaskDTO> module(RecomputeQuotaTeamMailboxesService service) {
        return DTOModule.forDomainObject(RecomputeQuotaTeamMailboxesTask.class)
            .convertToDTO(RecomputeQuotaTeamMailboxesTaskDTO.class)
            .toDomainObjectConverter(dto -> new RecomputeQuotaTeamMailboxesTask(service, Domain.of(dto.domain())))
            .toDTOConverter(((domainObject, typeName) ->
                new RecomputeQuotaTeamMailboxesTaskDTO(typeName, domainObject.getTeamMailboxDomain().asString())))
            .typeName(RecomputeQuotaTeamMailboxesTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    @Override
    @JsonIgnore
    public String getType() {
        return type;
    }
}
