package com.linagora.tmail.rate.limiter.api.cassandra.module

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.rate.limiter.api.cassandra.table.{CassandraRateLimitPlanTable, CassandraRateLimitPlanUserTable}
import com.linagora.tmail.rate.limiter.api.cassandra.{CassandraRateLimitationPlanRepository, CassandraRateLimitingPlanUserRepository}
import com.linagora.tmail.rate.limiter.api.{RateLimitationPlanRepository, RateLimitingPlanUserRepository}
import org.apache.james.backends.cassandra.components.CassandraModule

class CassandraRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[CassandraRateLimitingPlanUserRepository])
    bind(classOf[RateLimitationPlanRepository]).to(classOf[CassandraRateLimitationPlanRepository])

    val multibinder = Multibinder.newSetBinder(binder, classOf[CassandraModule])
    multibinder.addBinding().toInstance(CassandraRateLimitPlanUserTable.MODULE)
    multibinder.addBinding().toInstance(CassandraRateLimitPlanTable.MODULE)
  }
}
