package com.linagora.tmail.rate.limiter.api.cassandra.module

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.rate.limiter.api.cassandra.table.{CassandraRateLimitPlanTable, CassandraRateLimitPlanUserTable}
import com.linagora.tmail.rate.limiter.api.cassandra.{CassandraRateLimitingPlanRepository, CassandraRateLimitingPlanUserRepository}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanRepository, RateLimitingPlanUserRepository}
import org.apache.james.backends.cassandra.components.CassandraModule

class CassandraRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[CassandraRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[CassandraRateLimitingPlanRepository])

    val multibinder = Multibinder.newSetBinder(binder, classOf[CassandraModule])
    multibinder.addBinding().toInstance(CassandraRateLimitPlanUserTable.MODULE)
    multibinder.addBinding().toInstance(CassandraRateLimitPlanTable.MODULE)
  }
}
