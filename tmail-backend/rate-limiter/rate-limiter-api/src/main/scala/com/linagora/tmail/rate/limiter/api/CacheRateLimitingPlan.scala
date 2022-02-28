package com.linagora.tmail.rate.limiter.api

import com.github.benmanes.caffeine.cache.{AsyncCacheLoader, AsyncLoadingCache, Caffeine}
import org.apache.james.metrics.api.GaugeRegistry
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono
import reactor.core.scheduler.Schedulers

import java.lang
import java.time.Duration
import java.util.concurrent.Executor
import scala.concurrent.ExecutionContext.Implicits.global
import scala.jdk.FutureConverters._

class CacheRateLimitingPlan(repository: RateLimitationPlanRepository, expireDuration: Duration, gaugeRegistry: GaugeRegistry,
                            gaugePrefix: Option[String] = None) extends RateLimitationPlanRepository {

  private val cacheLoaderGet: AsyncCacheLoader[RateLimitingPlanId, RateLimitingPlan] =
    (key: RateLimitingPlanId, executor: Executor) => Mono.from(repository.get(key))
      .subscribeOn(Schedulers.fromExecutor(executor))
      .toFuture

  private val gaugePrefixValue: String = gaugePrefix.map(_ + ".").getOrElse("")

  private val cacheGet: AsyncLoadingCache[RateLimitingPlanId, RateLimitingPlan] = {
    val loadingCache: AsyncLoadingCache[RateLimitingPlanId, RateLimitingPlan] = Caffeine.newBuilder()
      .expireAfterWrite(expireDuration)
      .recordStats()
      .buildAsync[RateLimitingPlanId, RateLimitingPlan](cacheLoaderGet)

    gaugeRegistry.register(gaugePrefixValue + "rate_limiting_plan.cache.get.hitRate", () => loadingCache.synchronous().stats().hitRate())
      .register(gaugePrefixValue + "rate_limiting_plan.cache.get.missCount", () => loadingCache.synchronous().stats().missCount())
      .register(gaugePrefixValue + "rate_limiting_plan.cache.get.hitCount", () => loadingCache.synchronous().stats().hitCount())
      .register(gaugePrefixValue + "rate_limiting_plan.cache.get.size", () => loadingCache.synchronous().estimatedSize())
    loadingCache
  }

  override def create(creationRequest: RateLimitingPlanCreateRequest): Publisher[RateLimitingPlan] =
    SMono.fromPublisher(repository.create(creationRequest))
      .doOnNext(_ => invalidCache())

  override def update(resetRequest: RateLimitingPlanResetRequest): Publisher[Unit] =
    SMono.fromPublisher(repository.update(resetRequest))
      .`then`(SMono.fromCallable(() => invalidCache()))

  override def get(id: RateLimitingPlanId): Publisher[RateLimitingPlan] =
    SMono.fromFuture(cacheGet.get(id).asScala)

  override def planExists(id: RateLimitingPlanId): Publisher[lang.Boolean] =
    SMono.fromFuture(cacheGet.get(id).asScala)
      .hasElement
      .onErrorResume {
        case _: RateLimitingPlanNotFoundException => SMono.just(false)
        case error => SMono.raiseError(error)
      }
      .map(_.booleanValue())

  override def list(): Publisher[RateLimitingPlan] = repository.list()

  private def invalidCache(): Unit = cacheGet.synchronous().invalidateAll()
}
