package com.linagora.tmail.rate.limiter.cassandra

import com.datastax.driver.core.DataType.{bigint, frozenList, text, uuid}
import com.datastax.driver.core.schemabuilder.Create
import com.datastax.driver.core.{CodecRegistry, DataType, ProtocolVersion, TupleType}
import org.apache.james.backends.cassandra.components.CassandraModule

object CassandraRateLimitPlanTable {
  val TABLE_NAME: String = "rate_limit_plan"
  val PLAN_ID: String = "plan_id"
  val PLAN_NAME: String = "plan_name"
  val OPERATION_LIMITATION_NAME: String = "operation_limitation_name";
  val RATE_LIMITATIONS: String = "rate_limitations"

  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Hold Rate Limiting Plan - Use to management.")
    .statement((statement: Create) => statement
      .addPartitionKey(PLAN_ID, uuid)
      .addStaticColumn(PLAN_NAME, text)
      .addClusteringColumn(OPERATION_LIMITATION_NAME, text)
      .addColumn(RATE_LIMITATIONS,
        frozenList(TupleType.of(ProtocolVersion.NEWEST_SUPPORTED, CodecRegistry.DEFAULT_INSTANCE,
          text(),
          bigint(),
          DataType.frozenMap(text, bigint())))))
    .build
}

object CassandraRateLimitPlanHeaderEntry {
  val RATE_LIMITATION_NAME_INDEX: Int = 0
  val RATE_LIMITATION_DURATION_INDEX: Int = 1
  val RATE_LIMITS_INDEX: Int = 2
}
