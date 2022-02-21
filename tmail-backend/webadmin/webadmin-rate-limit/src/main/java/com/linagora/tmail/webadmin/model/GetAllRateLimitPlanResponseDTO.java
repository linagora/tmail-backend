package com.linagora.tmail.webadmin.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

public class GetAllRateLimitPlanResponseDTO {
    private final List<RateLimitingPlanDTO> list;

    public GetAllRateLimitPlanResponseDTO(List<RateLimitingPlanDTO> list) {
        this.list = list;
    }

    @JsonValue
    public List<RateLimitingPlanDTO> getList() {
        return list;
    }
}
