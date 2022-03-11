package com.linagora.tmail.rate.limiter.api

import org.apache.james.metrics.api.NoopGaugeRegistry
import org.junit.jupiter.api.BeforeEach

import java.time.Duration

class CacheInMemoryRateLimitingPlanRepositoryTest extends RateLimitingPlanRepositoryContract {
  var repository: RateLimitingPlanRepository = _

  override def testee: RateLimitingPlanRepository = repository

  @BeforeEach
  def beforeEach(): Unit = {
    val inMemoryRepository: RateLimitingPlanRepository = new InMemoryRateLimitingPlanRepository()
    repository = new CacheRateLimitingPlan(inMemoryRepository, Duration.ofMinutes(2), new NoopGaugeRegistry)
  }
}