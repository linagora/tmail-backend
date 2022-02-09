package com.linagora.tmail.rate.limiter.api

import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.collection.concurrent.Map

trait RateLimitationPlanRepository {

  def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan]

  def update(resetRequest: RateLimitingPlanResetRequest): Publisher[RateLimitingPlan]

  def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan]

  def planExists(id: RateLimitingPlanId): Publisher[Boolean]

  def list(): Publisher[RateLimitingPlan]
}

case class RateLimitingPlanNotFoundException() extends RuntimeException

class InMemoryRateLimitationPlanRepository() extends RateLimitationPlanRepository {

  val rateLimitingPlanStore: Map[RateLimitingPlanId, RateLimitingPlan] = scala.collection.concurrent.TrieMap()

  override def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan] =
    SMono.fromCallable(() => {
      val rateLimitingPlan: RateLimitingPlan = RateLimitingPlan.from(creationRequest)
      rateLimitingPlanStore.put(rateLimitingPlan.id, rateLimitingPlan)
      rateLimitingPlan
    })

  override def update(resetRequest: RateLimitingPlanResetRequest): Publisher[RateLimitingPlan] =
    SMono.justOrEmpty(rateLimitingPlanStore.get(resetRequest.id))
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))
      .map(rateLimitingPlan => {
        val newRateLimitingPlan: RateLimitingPlan = RateLimitingPlan.update(rateLimitingPlan, resetRequest)
        rateLimitingPlanStore.update(newRateLimitingPlan.id, newRateLimitingPlan)
        newRateLimitingPlan
      })

  override def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan] =
    SMono.justOrEmpty(rateLimitingPlanStore.get(id))
      .switchIfEmpty(SMono.error(new RateLimitingPlanNotFoundException))


  override def list(): Publisher[RateLimitingPlan] =
    SFlux.fromIterable(rateLimitingPlanStore)
      .map(_._2)

  override def planExists(id: RateLimitingPlanId): Publisher[Boolean] = SMono.justOrEmpty(rateLimitingPlanStore.get(id)).hasElement
}