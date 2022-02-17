package com.linagora.tmail.rate.limiter.cassandra

import com.google.common.base.Preconditions
import com.linagora.tmail.rate.limiter.api.{RateLimitationPlanRepository, RateLimitingPlan, RateLimitingPlanCreateRequest, RateLimitingPlanId, RateLimitingPlanName, RateLimitingPlanNotFoundException, RateLimitingPlanResetRequest}
import org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import javax.inject.Inject

class CassandraRateLimitationPlanRepository @Inject()(cassandraRateLimitPlanDAO: CassandraRateLimitPlanDAO) extends RateLimitationPlanRepository {

  override def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan] =
    SMono.fromCallable(() => RateLimitingPlanId.generate)
      .flatMap(planId => create(planId, RateLimitingPlanEntry.from(planId, creationRequest)))
      .flatMap(planId => SMono.fromPublisher(get(planId)))

  private def create(planId: RateLimitingPlanId, insertEntries: Seq[RateLimitingPlanEntry]): SMono[RateLimitingPlanId] =
    SFlux.fromIterable(insertEntries)
      .flatMap(insertEntry => cassandraRateLimitPlanDAO.insert(insertEntry))
      .`then`()
      .`then`(SMono.just(planId))

  override def update(resetRequest: RateLimitingPlanResetRequest): Publisher[Unit] =
    SMono.fromPublisher(cassandraRateLimitPlanDAO.planExists(resetRequest.id))
      .filter(exists => exists)
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))
      .flatMap(_ => cassandraRateLimitPlanDAO.delete(resetRequest.id))
      .`then`(create(resetRequest.id, RateLimitingPlanEntry.from(resetRequest)))
      .`then`()

  override def planExists(id: RateLimitingPlanId): Publisher[Boolean] = cassandraRateLimitPlanDAO.planExists(id)

  override def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan] =
    cassandraRateLimitPlanDAO.list(id)
      .collectSeq()
      .filter(_.nonEmpty)
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))
      .map(convertEntriesToRateLimitingPlan)

  override def list(): Publisher[RateLimitingPlan] =
    cassandraRateLimitPlanDAO.list()
      .groupBy(planEntry => planEntry.planId)
      .flatMap(planEntryGroupFlux => planEntryGroupFlux.collectSeq(), DEFAULT_CONCURRENCY)
      .map(convertEntriesToRateLimitingPlan)

  private def convertEntriesToRateLimitingPlan(entries: Seq[RateLimitingPlanEntry]): RateLimitingPlan = {
    Preconditions.checkArgument(entries.nonEmpty)
    RateLimitingPlan(id = RateLimitingPlanId(entries.head.planId),
      name = RateLimitingPlanName.liftOrThrow(entries.head.planName),
      operationLimitations = entries.map(_.operationLimitations))
  }
}

