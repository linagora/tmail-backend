package com.linagora.tmail.rate.limiter.api.postgres.dao;

import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.OPERATION_LIMITATION_NAME;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.PLAN_ID;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.PLAN_NAME;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.RATE_LIMITATIONS;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.TABLE_NAME;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.jooq.JSON;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.linagora.tmail.rate.limiter.api.LimitType;
import com.linagora.tmail.rate.limiter.api.LimitTypes;
import com.linagora.tmail.rate.limiter.api.OperationLimitations;
import com.linagora.tmail.rate.limiter.api.RateLimitation;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.postgres.RateLimitingPlanEntry;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;

public class PostgresRateLimitingPlanDAO {
    public static class RateLimitationsDTO {
        private String rateLimitationName;
        private Long rateLimitationPeriod;
        private Map<String, Long> limitMap;

        @JsonCreator
        public RateLimitationsDTO(@JsonProperty("rateLimitationName") String rateLimitationName,
                                  @JsonProperty("rateLimitationPeriod") Long rateLimitationPeriod,
                                  @JsonProperty("limitMap")Map<String, Long> limitMap) {
            this.rateLimitationName = rateLimitationName;
            this.rateLimitationPeriod = rateLimitationPeriod;
            this.limitMap = limitMap;
        }

        public String getRateLimitationName() {
            return rateLimitationName;
        }

        public Long getRateLimitationPeriod() {
            return rateLimitationPeriod;
        }

        public Map<String, Long> getLimitMap() {
            return limitMap;
        }
    }

    private final PostgresExecutor postgresExecutor;
    private final ObjectMapper objectMapper;

    @Inject
    public PostgresRateLimitingPlanDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
        objectMapper = new ObjectMapper();
    }

    public Mono<Void> savePlans(List<RateLimitingPlanEntry> planEntryList) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(planEntryList
            .stream()
            .map(planEntry -> dslContext.insertInto(TABLE_NAME)
                .set(PLAN_ID, planEntry.planId())
                .set(PLAN_NAME, planEntry.planName())
                .set(OPERATION_LIMITATION_NAME, planEntry.operationLimitations().asString())
                .set(RATE_LIMITATIONS, toJSON(CollectionConverters.asJava(planEntry.operationLimitations().rateLimitations()))))
            .toList())));
    }

    public Mono<Void> updatePlans(List<RateLimitingPlanEntry> planEntryList) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(planEntryList
            .stream()
            .map(planEntry -> dslContext.update(TABLE_NAME)
                .set(PLAN_NAME, planEntry.planName())
                .set(OPERATION_LIMITATION_NAME, planEntry.operationLimitations().asString())
                .set(RATE_LIMITATIONS, toJSON(CollectionConverters.asJava(planEntry.operationLimitations().rateLimitations())))
                .where(PLAN_ID.eq(planEntry.planId())))
            .toList())));
    }

    public Flux<RateLimitingPlanEntry> getPlans(RateLimitingPlanId planId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
            .where(PLAN_ID.eq(planId.value()))))
            .map(record -> new RateLimitingPlanEntry(record.get(PLAN_ID),
                record.get(PLAN_NAME),
                OperationLimitations.liftOrThrow(record.get(OPERATION_LIMITATION_NAME),
                    CollectionConverters.asScala(toRateLimitationList(record.get(RATE_LIMITATIONS))).toSeq())));
    }

    public Flux<RateLimitingPlanEntry> getPlans() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)))
            .map(record -> new RateLimitingPlanEntry(record.get(PLAN_ID),
                record.get(PLAN_NAME),
                OperationLimitations.liftOrThrow(record.get(OPERATION_LIMITATION_NAME),
                    CollectionConverters.asScala(toRateLimitationList(record.get(RATE_LIMITATIONS))).toSeq())));
    }

    public Mono<Boolean> planExists(RateLimitingPlanId id) {
        return postgresExecutor.executeExists(dslContext -> dslContext.selectFrom(TABLE_NAME)
            .where(PLAN_ID.eq(id.value())));
    }

    private JSON toJSON(List<RateLimitation> rateLimitations) {
        try {
            return JSON.json(objectMapper.writeValueAsString(rateLimitations.stream()
                .map(rateLimitation -> new RateLimitationsDTO(rateLimitation.name(),
                    rateLimitation.period().toMillis(),
                    CollectionConverters.asJava(rateLimitation.limitsValue()).stream()
                        .collect(ImmutableMap.toImmutableMap(LimitType::asString,
                            PostgresRateLimitingDAOUtils::getQuantity))))
                .toList()));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<RateLimitation> toRateLimitationList(JSON json) {
        try {
            return objectMapper.readValue(json.data(), new TypeReference<List<RateLimitationsDTO>>() {})
                .stream()
                .map(rateLimitationsDTO -> new RateLimitation(rateLimitationsDTO.getRateLimitationName(),
                    Duration.ofMillis(rateLimitationsDTO.getRateLimitationPeriod()),
                    LimitTypes.fromMutableMap(CollectionConverters.asScala(rateLimitationsDTO.getLimitMap()
                        .entrySet()
                        .stream()
                        .collect(ImmutableMap.toImmutableMap(Map.Entry::getKey, Map.Entry::getValue))))))
                .toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
