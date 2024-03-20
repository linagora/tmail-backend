package com.linagora.tmail.rate.limiter.api.postgres;

import java.util.List;

import javax.inject.Inject;

import org.reactivestreams.Publisher;

import com.google.common.base.Preconditions;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlan;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanCreateRequest;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanName;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanNotFoundException;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanRepository;
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanResetRequest;
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitingPlanDAO;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.CollectionConverters;
import scala.runtime.BoxedUnit;

public class PostgresRateLimitingPlanRepository implements RateLimitingPlanRepository {
    private final PostgresRateLimitingPlanDAO rateLimitingPlanRepositoryDAO;

    @Inject
    public PostgresRateLimitingPlanRepository(PostgresRateLimitingPlanDAO rateLimitingPlanRepositoryDAO) {
        this.rateLimitingPlanRepositoryDAO = rateLimitingPlanRepositoryDAO;
    }

    @Override
    public Publisher<RateLimitingPlan> create(RateLimitingPlanCreateRequest creationRequest) {
        RateLimitingPlanId planId = RateLimitingPlanId.generate();
        return rateLimitingPlanRepositoryDAO.savePlans(RateLimitingPlanEntry.from(planId, creationRequest))
            .then(Mono.from(get(planId)));
    }

    @Override
    public Publisher<BoxedUnit> update(RateLimitingPlanResetRequest resetRequest) {
        return rateLimitingPlanRepositoryDAO.planExists(resetRequest.id())
            .filter(exists -> exists)
            .flatMap(exists -> rateLimitingPlanRepositoryDAO.updatePlans(RateLimitingPlanEntry.from(resetRequest))
                .then(Mono.just(BoxedUnit.UNIT)))
            .switchIfEmpty(Mono.error(new RateLimitingPlanNotFoundException()));
    }

    @Override
    public Publisher<RateLimitingPlan> get(RateLimitingPlanId id) {
        return rateLimitingPlanRepositoryDAO.getPlans(id)
            .collectList()
            .filter(rateLimitingPlanEntries -> !rateLimitingPlanEntries.isEmpty())
            .map(this::convertEntriesToRateLimitingPlan)
            .switchIfEmpty(Mono.error(new RateLimitingPlanNotFoundException()));
    }

    @Override
    public Publisher<Boolean> planExists(RateLimitingPlanId id) {
        return rateLimitingPlanRepositoryDAO.planExists(id);
    }

    @Override
    public Publisher<RateLimitingPlan> list() {
        return rateLimitingPlanRepositoryDAO.getPlans()
            .groupBy(RateLimitingPlanEntry::planId)
            .flatMap(Flux::collectList)
            .map(this::convertEntriesToRateLimitingPlan);
    }

    private RateLimitingPlan convertEntriesToRateLimitingPlan(List<RateLimitingPlanEntry> rateLimitingPlanEntries) {
        Preconditions.checkArgument(!rateLimitingPlanEntries.isEmpty());
        return new RateLimitingPlan(new RateLimitingPlanId(rateLimitingPlanEntries.get(0).planId()),
            RateLimitingPlanName.liftOrThrow(rateLimitingPlanEntries.get(0).planName()),
            CollectionConverters.asScala(rateLimitingPlanEntries.stream().map(RateLimitingPlanEntry::operationLimitations).toList()).toSeq());
    }
}
