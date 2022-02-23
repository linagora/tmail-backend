package com.linagora.tmail.webadmin.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.google.common.base.Preconditions;

public class OperationLimitationsDTO {
    private final List<RateLimitationDTO> rateLimitationDTOList;

    @JsonCreator
    public OperationLimitationsDTO(List<RateLimitationDTO> rateLimitationDTOList) {
        Preconditions.checkArgument(!rateLimitationDTOList.isEmpty(), "Operation limitation arrays must have at least one entry.");
        this.rateLimitationDTOList = rateLimitationDTOList;
    }

    @JsonValue
    public List<RateLimitationDTO> getRateLimitationDTOList() {
        return rateLimitationDTOList;
    }
}
