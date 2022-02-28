package com.linagora.tmail.rate.limiter.api

import org.apache.james.metrics.api.NoopGaugeRegistry
import org.junit.jupiter.api.BeforeEach

import java.time.Duration

class CacheInMemoryRateLimitingPlanRepositoryTest extends RateLimitationPlanRepositoryContract {
  var repository: RateLimitationPlanRepository = _

  override def testee: RateLimitationPlanRepository = repository

  @BeforeEach
  def beforeEach(): Unit = {
    val inMemoryRepository: RateLimitationPlanRepository = new InMemoryRateLimitationPlanRepository()
    repository = new CacheRateLimitingPlan(inMemoryRepository, Duration.ofMinutes(2), new NoopGaugeRegistry)
  }
}