package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitingPlanDTO {
    private final String planId;
    private final String planName;
    private final Optional<OperationLimitationsDTO> transitLimits;
    private final Optional<OperationLimitationsDTO> relayLimits;
    private final Optional<OperationLimitationsDTO> deliveryLimits;

    public RateLimitingPlanDTO(@JsonProperty("planId") String planId,
                               @JsonProperty("planName") String planName,
                               @JsonProperty("transitLimits") Optional<OperationLimitationsDTO> transitLimitationsDTO,
                               @JsonProperty("relayLimits") Optional<OperationLimitationsDTO> relayLimitationsDTO,
                               @JsonProperty("deliveryLimits") Optional<OperationLimitationsDTO> deliveryLimitationsDTO) {
        this.planId = planId;
        this.planName = planName;
        this.transitLimits = transitLimitationsDTO;
        this.relayLimits = relayLimitationsDTO;
        this.deliveryLimits = deliveryLimitationsDTO;
    }

    public String getPlanId() {
        return planId;
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
