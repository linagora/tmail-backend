package com.linagora.tmail.rate.limiter.api

import java.time.Duration

import org.apache.james.metrics.api.NoopGaugeRegistry
import org.junit.jupiter.api.BeforeEach

class CacheInMemoryRateLimitingPlanRepositoryTest extends RateLimitingPlanRepositoryContract {
  var repository: RateLimitingPlanRepository = _

  override def testee: RateLimitingPlanRepository = repository

  @BeforeEach
  def beforeEach(): Unit = {
    val inMemoryRepository: RateLimitingPlanRepository = new InMemoryRateLimitingPlanRepository()
    repository = new CacheRateLimitingPlan(inMemoryRepository, Duration.ofMinutes(2), new NoopGaugeRegistry)
  }
}