package com.linagora.tmail.rate.limiter.api.cassandra.module

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.cassandra.dao.{CassandraRateLimitPlanDAO, CassandraRateLimitPlanUserDAO}
import com.linagora.tmail.rate.limiter.api.cassandra.table.{CassandraRateLimitPlanTable, CassandraRateLimitPlanUserTable}
import com.linagora.tmail.rate.limiter.api.cassandra.{CassandraRateLimitingPlanRepository, CassandraRateLimitingPlanUserRepository}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanRepository, RateLimitingPlanUserRepository, RateLimitingPlanUsernameChangeTaskStep}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.user.api.UsernameChangeTaskStep

class CassandraRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraRateLimitPlanDAO]).in(Scopes.SINGLETON)
    bind(classOf[CassandraRateLimitPlanUserDAO]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[CassandraRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[CassandraRateLimitingPlanRepository])

    val multibinder = Multibinder.newSetBinder(binder, classOf[CassandraModule])
    multibinder.addBinding().toInstance(CassandraRateLimitPlanUserTable.MODULE)
    multibinder.addBinding().toInstance(CassandraRateLimitPlanTable.MODULE)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingPlanUsernameChangeTaskStep])
  }
}
