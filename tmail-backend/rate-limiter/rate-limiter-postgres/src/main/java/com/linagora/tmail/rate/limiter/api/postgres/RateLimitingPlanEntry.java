package com.linagora.tmail.rate.limiter.api.postgres;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.rate.limiter.api.LimitType;
import com.linagora.tmail.rate.limiter.api.OperationLimitations;
import com.linagora.tmail.rate.limiter.api.RateLimitation;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanCreateRequest;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanResetRequest;
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitingDAOUtils;

import scala.jdk.javaapi.CollectionConverters;

public record RateLimitingPlanEntry(UUID planId,
                                    String planName,
                                    String operationLimitationName,
                                    List<RateLimitationsDTO> rateLimitationsDTOS) {

    static List<RateLimitingPlanEntry> from(RateLimitingPlanId rateLimitingPlanId, RateLimitingPlanCreateRequest createRequest) {
        return CollectionConverters.asJava(createRequest.operationLimitationsValue()).stream()
            .map(limitations -> new RateLimitingPlanEntry(rateLimitingPlanId.value(),
                createRequest.nameValue(),
                limitations.asString(),
                RateLimitationsDTO.listFrom(CollectionConverters.asJava(limitations.rateLimitations()))))
            .toList();
    }

    static List<RateLimitingPlanEntry> from(RateLimitingPlanResetRequest resetRequest) {
        return CollectionConverters.asJava(resetRequest.operationLimitationsValue()).stream()
            .map(limitations -> new RateLimitingPlanEntry(resetRequest.id().value(),
                resetRequest.nameValue(),
                limitations.asString(),
                RateLimitationsDTO.listFrom(CollectionConverters.asJava(limitations.rateLimitations()))))
            .toList();
    }

    public record RateLimitationsDTO(@JsonProperty("rateLimitationName") String rateLimitationName,
                                     @JsonProperty("rateLimitationPeriod") Long rateLimitationPeriod,
                                     @JsonProperty("limitMap") Map<String, Long> limitMap) {
        public static RateLimitationsDTO from(RateLimitation rateLimitation) {
            return new RateLimitationsDTO(rateLimitation.name(),
                rateLimitation.period().toMillis(),
                CollectionConverters.asJava(rateLimitation.limitsValue()).stream()
                    .collect(ImmutableMap.toImmutableMap(LimitType::asString,
                        PostgresRateLimitingDAOUtils::getQuantity)));
        }

        public static List<RateLimitationsDTO> listFrom(List<RateLimitation> rateLimitations) {
            return rateLimitations.stream()
                .map(RateLimitationsDTO::from)
                .toList();
        }

        public RateLimitation asRateLimitation() {
            return new RateLimitation(rateLimitationName(),
                Duration.ofMillis(rateLimitationPeriod()),
                PostgresRateLimitingDAOUtils.getLimitType(limitMap()));
        }
    }

    public OperationLimitations operationLimitations() {
        return OperationLimitations.liftOrThrow(
            operationLimitationName(),
            CollectionConverters.asScala(rateLimitationsDTOS().stream()
                .map(RateLimitationsDTO::asRateLimitation)
                .toList()).toSeq());
    }

}
