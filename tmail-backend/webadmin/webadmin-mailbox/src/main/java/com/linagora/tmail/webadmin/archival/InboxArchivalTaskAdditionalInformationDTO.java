package com.linagora.tmail.webadmin.archival;

import java.time.Instant;

import org.apache.james.json.DTOModule;
import org.apache.james.server.task.json.dto.AdditionalInformationDTO;
import org.apache.james.server.task.json.dto.AdditionalInformationDTOModule;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InboxArchivalTaskAdditionalInformationDTO implements AdditionalInformationDTO {
    
    public static final AdditionalInformationDTOModule<InboxArchivalTask.AdditionalInformation, InboxArchivalTaskAdditionalInformationDTO> SERIALIZATION_MODULE =
        DTOModule.forDomainObject(InboxArchivalTask.AdditionalInformation.class)
            .convertToDTO(InboxArchivalTaskAdditionalInformationDTO.class)
            .toDomainObjectConverter(InboxArchivalTaskAdditionalInformationDTO::toDomainObject)
            .toDTOConverter(InboxArchivalTaskAdditionalInformationDTO::toDto)
            .typeName(InboxArchivalTask.TASK_TYPE.asString())
            .withFactory(AdditionalInformationDTOModule::new);

    private static InboxArchivalTask.AdditionalInformation toDomainObject(InboxArchivalTaskAdditionalInformationDTO dto) {
        return new InboxArchivalTask.AdditionalInformation(dto.timestamp, dto.archivedMessageCount, dto.errorMessageCount);
    }

    private static InboxArchivalTaskAdditionalInformationDTO toDto(InboxArchivalTask.AdditionalInformation domainObject, String type) {
        return new InboxArchivalTaskAdditionalInformationDTO(type, domainObject.timestamp(), domainObject.getArchivedMessageCount(), domainObject.getErrorMessageCount());
    }

    private final String type;
    private final Instant timestamp;
    private final long archivedMessageCount;
    private final long errorMessageCount;

    public InboxArchivalTaskAdditionalInformationDTO(@JsonProperty("type") String type,
                                                     @JsonProperty("timestamp") Instant timestamp,
                                                     @JsonProperty("archivedMessageCount") long archivedMessageCount,
                                                     @JsonProperty("errorMessageCount") long errorMessageCount) {
        this.type = type;
        this.timestamp = timestamp;
        this.archivedMessageCount = archivedMessageCount;
        this.errorMessageCount = errorMessageCount;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    public long getArchivedMessageCount() {
        return archivedMessageCount;
    }

    public long getErrorMessageCount() {
        return errorMessageCount;
    }

}
