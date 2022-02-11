package com.linagora.tmail.rate.limiter.api.cassandra.module

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanUserRepository
import com.linagora.tmail.rate.limiter.api.cassandra.CassandraRateLimitingPlanUserRepository
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanUserTable
import org.apache.james.backends.cassandra.components.CassandraModule

class CassandraRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[CassandraRateLimitingPlanUserRepository])

    Multibinder.newSetBinder(binder, classOf[CassandraModule])
      .addBinding()
      .toInstance(CassandraRateLimitPlanUserTable.MODULE)
  }
}
