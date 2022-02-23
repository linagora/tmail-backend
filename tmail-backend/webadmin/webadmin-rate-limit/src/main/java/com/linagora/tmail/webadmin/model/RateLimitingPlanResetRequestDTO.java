package com.linagora.tmail.webadmin.model;

import static com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.DELIVERY_LIMIT_KEY;
import static com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.RELAY_LIMIT_KEY;
import static com.linagora.tmail.webadmin.model.RateLimitingPlanCreateRequestDTO.TRANSIT_LIMIT_KEY;

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
                                           @JsonProperty(TRANSIT_LIMIT_KEY) Optional<OperationLimitationsDTO> transitLimitationsDTO,
                                           @JsonProperty(RELAY_LIMIT_KEY) Optional<OperationLimitationsDTO> relayLimitationsDTO,
                                           @JsonProperty(DELIVERY_LIMIT_KEY) Optional<OperationLimitationsDTO> deliveryLimitationsDTO) {
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
