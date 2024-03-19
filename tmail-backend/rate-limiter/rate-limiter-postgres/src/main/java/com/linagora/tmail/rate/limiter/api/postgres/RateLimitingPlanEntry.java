package com.linagora.tmail.rate.limiter.api.postgres;

import java.util.List;
import java.util.UUID;

import com.linagora.tmail.rate.limiter.api.OperationLimitations;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanCreateRequest;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanResetRequest;

import scala.jdk.javaapi.CollectionConverters;

public record RateLimitingPlanEntry(UUID planId,
                                    String planName,
                                    OperationLimitations operationLimitations) {
    static List<RateLimitingPlanEntry> from(RateLimitingPlanId rateLimitingPlanId, RateLimitingPlanCreateRequest createRequest) {
        return CollectionConverters.asJava(createRequest.operationLimitationsValue()).stream()
            .map(limitations -> new RateLimitingPlanEntry(rateLimitingPlanId.value(),
                createRequest.nameValue(),
                limitations))
            .toList();
    }

    static List<RateLimitingPlanEntry> from(RateLimitingPlanResetRequest resetRequest) {
        return CollectionConverters.asJava(resetRequest.operationLimitationsValue()).stream()
            .map(limitations -> new RateLimitingPlanEntry(resetRequest.id().value(),
                resetRequest.nameValue(),
                limitations))
            .toList();
    }
}
