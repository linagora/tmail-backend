package com.linagora.tmail.webadmin.model;

import java.util.HashMap;
import java.util.Optional;

public class RateLimitingPlanCreateRequestDTO extends HashMap<String, Optional<OperationLimitationsDTO>> {
    public static final String TRANSIT_LIMIT_KEY = "transitLimits";
    public static final String DELIVERY_LIMIT_KEY = "deliveryLimits";
    public static final String RELAY_LIMIT_KEY = "relayLimits";
}
