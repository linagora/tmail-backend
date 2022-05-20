package com.linagora.tmail.webadmin.model;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RateLimitingPlanDTO(@JsonProperty("planId") String planId,
                                  @JsonProperty("planName") String planName,
                                  @JsonProperty("transitLimits") Optional<OperationLimitationsDTO> transitLimits,
                                  @JsonProperty("relayLimits") Optional<OperationLimitationsDTO> relayLimits,
                                  @JsonProperty("deliveryLimits") Optional<OperationLimitationsDTO> deliveryLimits) {
}
