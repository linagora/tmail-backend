package com.linagora.tmail.rate.limiter.api.postgres.module

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.postgres.PostgresRateLimitingPlanRepository
import com.linagora.tmail.rate.limiter.api.postgres.repository.PostgresRateLimitingPlanUserRepository
import com.linagora.tmail.rate.limiter.api.postgres.table.{PostgresRateLimitPlanModule, PostgresRateLimitPlanUserTable}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanRepository, RateLimitingPlanUserRepository, RateLimitingPlanUsernameChangeTaskStep}
import org.apache.james.backends.postgres.PostgresModule
import org.apache.james.user.api.UsernameChangeTaskStep

class PostgresRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[PostgresRateLimitingPlanUserRepository]).in(Scopes.SINGLETON)
    bind(classOf[PostgresRateLimitingPlanRepository]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[PostgresRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[PostgresRateLimitingPlanRepository])

    val postgresRateLimitingDataDefinitions = Multibinder.newSetBinder(binder, classOf[PostgresModule])
    postgresRateLimitingDataDefinitions.addBinding().toInstance(PostgresRateLimitPlanUserTable.MODULE)
    postgresRateLimitingDataDefinitions.addBinding().toInstance(PostgresRateLimitPlanModule.MODULE)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingPlanUsernameChangeTaskStep])
  }
}
