package com.linagora.tmail.rate.limiter.api.postgres.dao;

import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.OPERATION_LIMITATION_NAME;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.PLAN_ID;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.PLAN_NAME;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.RATE_LIMITATIONS;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.TABLE_NAME;

import java.util.List;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.jooq.JSON;
import org.jooq.Record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.postgres.RateLimitingPlanEntry;
import com.linagora.tmail.rate.limiter.api.postgres.RateLimitingPlanEntry.RateLimitationsDTO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresRateLimitingPlanDAO {
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
                .set(OPERATION_LIMITATION_NAME, planEntry.operationLimitationName())
                .set(RATE_LIMITATIONS, toJSON(planEntry.rateLimitationsDTOS())))
            .toList())));
    }

    public Mono<Void> updatePlans(List<RateLimitingPlanEntry> planEntryList) {
        return postgresExecutor.executeVoid(dslContext -> Mono.from(dslContext.batch(planEntryList
            .stream()
            .map(planEntry -> dslContext.update(TABLE_NAME)
                .set(PLAN_NAME, planEntry.planName())
                .set(OPERATION_LIMITATION_NAME, planEntry.operationLimitationName())
                .set(RATE_LIMITATIONS, toJSON(planEntry.rateLimitationsDTOS()))
                .where(PLAN_ID.eq(planEntry.planId())))
            .toList())));
    }

    public Flux<RateLimitingPlanEntry> getPlans(RateLimitingPlanId planId) {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)
            .where(PLAN_ID.eq(planId.value()))))
            .map(recordRateLimitingPlanEntryFunction());
    }

    public Flux<RateLimitingPlanEntry> getPlans() {
        return postgresExecutor.executeRows(dslContext -> Flux.from(dslContext.selectFrom(TABLE_NAME)))
            .map(recordRateLimitingPlanEntryFunction());
    }

    private Function<Record, RateLimitingPlanEntry> recordRateLimitingPlanEntryFunction() {
        return record -> new RateLimitingPlanEntry(record.get(PLAN_ID),
            record.get(PLAN_NAME),
            record.get(OPERATION_LIMITATION_NAME),
            toRateLimitationsDTOList(record.get(RATE_LIMITATIONS)));
    }

    public Mono<Boolean> planExists(RateLimitingPlanId id) {
        return postgresExecutor.executeExists(dslContext -> dslContext.select(PLAN_ID)
            .from(TABLE_NAME)
            .where(PLAN_ID.eq(id.value())));
    }

    private JSON toJSON(List<RateLimitationsDTO> rateLimitations) {
        try {
            return JSON.json(objectMapper.writeValueAsString(rateLimitations));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private List<RateLimitationsDTO> toRateLimitationsDTOList(JSON json) {
        try {
            return objectMapper.readValue(json.data(), new TypeReference<List<RateLimitationsDTO>>() {});
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
