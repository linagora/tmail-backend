package com.linagora.tmail.rate.limiter.api.cassandra.table

import com.datastax.oss.driver.api.core.`type`.DataTypes
import org.apache.james.backends.cassandra.components.CassandraModule

object CassandraRateLimitPlanUserTable {
  val TABLE_NAME = "rate_limit_plan_user"
  val USERNAME = "username"
  val PLAN_ID = "plan_id"

  val MODULE: CassandraModule = CassandraModule
    .table(TABLE_NAME)
    .comment("Hold User - Rate limiting plan mapping data. Use to manage user plan.")
    .statement(statement => types => statement
      .withPartitionKey(USERNAME, DataTypes.TEXT)
      .withColumn(PLAN_ID, DataTypes.UUID))
    .build
}
