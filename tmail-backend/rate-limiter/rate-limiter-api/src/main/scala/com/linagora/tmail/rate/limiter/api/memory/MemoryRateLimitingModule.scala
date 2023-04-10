package com.linagora.tmail.rate.limiter.api.memory

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.{InMemoryRateLimitingPlanRepository, RateLimitingPlanRepository, RateLimitingPlanUserRepository, RateLimitingPlanUsernameChangeTaskStep}
import org.apache.james.user.api.UsernameChangeTaskStep

class MemoryRateLimitingModule() extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MemoryRateLimitingPlanUserRepository]).in(Scopes.SINGLETON)
    bind(classOf[InMemoryRateLimitingPlanRepository]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[MemoryRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[InMemoryRateLimitingPlanRepository])

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingPlanUsernameChangeTaskStep])
  }
}
