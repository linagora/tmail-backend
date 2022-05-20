package com.linagora.tmail.webadmin.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

public record GetAllRateLimitPlanResponseDTO(List<RateLimitingPlanDTO> list) {
    @Override
    @JsonValue
    public List<RateLimitingPlanDTO> list() {
        return list;
    }
}
