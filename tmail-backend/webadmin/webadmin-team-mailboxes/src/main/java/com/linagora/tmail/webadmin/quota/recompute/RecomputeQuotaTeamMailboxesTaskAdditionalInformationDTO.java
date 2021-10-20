package com.linagora.tmail.webadmin.quota.recompute;

import java.time.Instant;

import org.apache.james.core.Domain;
import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.google.common.collect.ImmutableList;

public class RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO implements AdditionalInformationDTO {

    public static final AdditionalInformationDTOModule<RecomputeQuotaTeamMailboxesTask.Details, RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(RecomputeQuotaTeamMailboxesTask.Details.class)
            .convertToDTO(RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(dto ->
                new RecomputeQuotaTeamMailboxesTask.Details(
                    dto.getTimestamp(),
                    Domain.of(dto.getDomain()),
                    dto.getProcessedQuotaRoots(),
                    dto.getFailedQuotaRoots()))
            .toDTOConverter(((domainObject, typeName) ->
                new RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO(
                    typeName, domainObject.timestamp(), domainObject.getDomain().asString(), domainObject.getProcessedQuotaRoots(), domainObject.getFailedQuotaRoots())))
            .typeName(RecomputeQuotaTeamMailboxesTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private final String type;
    private final Instant timestamp;
    private final String domain;
    private final long processedQuotaRoots;
    private final ImmutableList<String> failedQuotaRoots;

    public RecomputeQuotaTeamMailboxesTaskAdditionalInformationDTO(String type,
                                                                   Instant timestamp,
                                                                   String domain,
                                                                   long processedQuotaRoots,
                                                                   ImmutableList<String> failedQuotaRoots) {
        this.type = type;
        this.timestamp = timestamp;
        this.domain = domain;
        this.processedQuotaRoots = processedQuotaRoots;
        this.failedQuotaRoots = failedQuotaRoots;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public String getDomain() {
        return domain;
    }

    public long getProcessedQuotaRoots() {
        return processedQuotaRoots;
    }

    public ImmutableList<String> getFailedQuotaRoots() {
        return failedQuotaRoots;
    }
}
