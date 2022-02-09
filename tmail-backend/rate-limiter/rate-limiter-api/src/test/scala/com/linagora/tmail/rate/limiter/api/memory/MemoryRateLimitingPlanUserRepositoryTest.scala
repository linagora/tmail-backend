package com.linagora.tmail.rate.limiter.api.memory

import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanUserRepository, RateLimitingPlanUserRepositoryContract}

class MemoryRateLimitingPlanUserRepositoryTest extends RateLimitingPlanUserRepositoryContract {
  val repository: RateLimitingPlanUserRepository = new MemoryRateLimitingPlanUserRepository

  override def testee: RateLimitingPlanUserRepository = repository
}
