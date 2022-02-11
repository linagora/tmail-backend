package com.linagora.tmail.rate.limiter.api.cassandra.table

import com.datastax.driver.core.DataType.{text, uuid}
import com.datastax.driver.core.schemabuilder.Create
import org.apache.james.backends.cassandra.components.CassandraModule

object CassandraRateLimitPlanUserTable {
  val TABLE_NAME = "rate_limit_plan_user"
  val USERNAME = "username"
  val PLAN_ID = "plan_id"

  val MODULE: CassandraModule = CassandraModule
    .table(TABLE_NAME)
    .comment("Hold User - Rate limiting plan mapping data. Use to manage user plan.")
    .statement((statement: Create) => statement
      .addPartitionKey(USERNAME, text)
      .addColumn(PLAN_ID, uuid))
    .build
}
