package com.linagora.tmail.webadmin.quota.recompute;

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.TaskDTO;
import org.apache.james.server.task.json.dto.TaskDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RecomputeQuotaTeamMailboxesTaskDTO implements TaskDTO {
    public static TaskDTOModule<RecomputeQuotaTeamMailboxesTask, RecomputeQuotaTeamMailboxesTaskDTO> module(RecomputeQuotaTeamMailboxesService service) {
        return DTOModule.forDomainObject(RecomputeQuotaTeamMailboxesTask.class)
            .convertToDTO(RecomputeQuotaTeamMailboxesTaskDTO.class)
            .toDomainObjectConverter(dto -> new RecomputeQuotaTeamMailboxesTask(service, Domain.of(dto.getDomain())))
            .toDTOConverter(((domainObject, typeName) ->
                new RecomputeQuotaTeamMailboxesTaskDTO(typeName, domainObject.getTeamMailboxDomain().asString())))
            .typeName(RecomputeQuotaTeamMailboxesTask.TASK_TYPE.asString())
            .withFactory(TaskDTOModule::new);
    }

    private final String type;
    private final String domain;

    public RecomputeQuotaTeamMailboxesTaskDTO(@JsonProperty("type") String type,
                                              @JsonProperty("domain") String domain) {
        this.type = type;
        this.domain = domain;
    }

    @Override
    public String getType() {
        return type;
    }

    public String getDomain() {
        return domain;
    }
}
