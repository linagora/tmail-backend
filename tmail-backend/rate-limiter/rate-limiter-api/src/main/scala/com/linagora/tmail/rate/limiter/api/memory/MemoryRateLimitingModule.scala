package com.linagora.tmail.rate.limiter.api.memory

import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.{InMemoryRateLimitationPlanRepository, RateLimitationPlanRepository, RateLimitingPlanUserRepository}

class MemoryRateLimitingModule() extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MemoryRateLimitingPlanUserRepository]).in(Scopes.SINGLETON)
    bind(classOf[InMemoryRateLimitationPlanRepository]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[MemoryRateLimitingPlanUserRepository])
    bind(classOf[RateLimitationPlanRepository]).to(classOf[InMemoryRateLimitationPlanRepository])
  }
}
