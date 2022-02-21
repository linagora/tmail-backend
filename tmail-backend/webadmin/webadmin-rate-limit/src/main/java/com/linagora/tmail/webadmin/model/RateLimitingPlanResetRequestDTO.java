package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitingPlanResetRequestDTO {
    private final String planName;
    private final Optional<OperationLimitationsDTO> transitLimits;
    private final Optional<OperationLimitationsDTO> relayLimits;
    private final Optional<OperationLimitationsDTO> deliveryLimits;

    @JsonCreator
    public RateLimitingPlanResetRequestDTO(@JsonProperty(value = "planName", required = true) String planName,
                                           @JsonProperty("transitLimits") Optional<OperationLimitationsDTO> transitLimitationsDTO,
                                           @JsonProperty("relayLimits") Optional<OperationLimitationsDTO> relayLimitationsDTO,
                                           @JsonProperty("deliveryLimits") Optional<OperationLimitationsDTO> deliveryLimitationsDTO) {
        this.planName = planName;
        this.transitLimits = transitLimitationsDTO;
        this.relayLimits = relayLimitationsDTO;
        this.deliveryLimits = deliveryLimitationsDTO;
    }

    public String getPlanName() {
        return planName;
    }

    public Optional<OperationLimitationsDTO> getTransitLimits() {
        return transitLimits;
    }

    public Optional<OperationLimitationsDTO> getRelayLimits() {
        return relayLimits;
    }

    public Optional<OperationLimitationsDTO> getDeliveryLimits() {
        return deliveryLimits;
    }
}
