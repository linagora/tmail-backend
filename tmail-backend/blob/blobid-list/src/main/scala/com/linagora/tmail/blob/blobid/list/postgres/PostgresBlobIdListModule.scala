package com.linagora.tmail.blob.blobid.list.postgres

import org.apache.james.backends.postgres.{PostgresDataDefinition, PostgresTable}
import org.jooq.impl.{DSL, SQLDataType}
import org.jooq.{Field, Record, Table}

object PostgresBlobIdListModule {
  val TABLE_NAME: Table[Record] = DSL.table("blob_id_list")

  val BLOB_ID: Field[String] = DSL.field("blob_id", SQLDataType.VARCHAR.notNull)

  val TABLE: PostgresTable = PostgresTable.name(TABLE_NAME.getName)
    .createTableStep((dsl, tableName) => dsl.createTableIfNotExists(tableName)
      .column(BLOB_ID)
      .constraint(DSL.primaryKey(BLOB_ID)))
    .supportsRowLevelSecurity
    .build

  val MODULE: PostgresDataDefinition = PostgresDataDefinition
    .builder
    .addTable(TABLE)
    .build
}
