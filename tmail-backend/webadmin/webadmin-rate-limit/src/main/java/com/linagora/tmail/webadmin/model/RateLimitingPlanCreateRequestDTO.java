package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class RateLimitingPlanCreateRequestDTO {
    private final Optional<OperationLimitationsDTO> transitLimits;
    private final Optional<OperationLimitationsDTO> relayLimits;
    private final Optional<OperationLimitationsDTO> deliveryLimits;

    @JsonCreator
    public RateLimitingPlanCreateRequestDTO(@JsonProperty("transitLimits") Optional<OperationLimitationsDTO> transitLimitationsDTO,
                                            @JsonProperty("relayLimits") Optional<OperationLimitationsDTO> relayLimitationsDTO,
                                            @JsonProperty("deliveryLimits") Optional<OperationLimitationsDTO> deliveryLimitationsDTO) {
        this.transitLimits = transitLimitationsDTO;
        this.relayLimits = relayLimitationsDTO;
        this.deliveryLimits = deliveryLimitationsDTO;
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
