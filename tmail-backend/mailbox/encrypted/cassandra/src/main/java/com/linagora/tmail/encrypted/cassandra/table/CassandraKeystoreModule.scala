package com.linagora.tmail.encrypted.cassandra.table

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows
import com.linagora.tmail.encrypted.cassandra.table.KeyStoreTable.{ID, KEY, TABLE_NAME, USERNAME}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraKeystoreModule {
  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Holds Keystore definition. Used to manage user public keys used for data encryption.")
    .options(options => options
      .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement(statement => types => statement
      .withPartitionKey(USERNAME, DataTypes.TEXT)
      .withClusteringColumn(ID, DataTypes.TEXT)
      .withColumn(KEY, DataTypes.BLOB))
    .build
}
