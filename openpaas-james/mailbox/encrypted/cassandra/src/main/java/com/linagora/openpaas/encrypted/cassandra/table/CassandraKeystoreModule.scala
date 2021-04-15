package com.linagora.openpaas.encrypted.cassandra.table

import com.datastax.driver.core.DataType.{blob, text}
import com.datastax.driver.core.schemabuilder.Create
import com.datastax.driver.core.schemabuilder.Create.Options
import com.datastax.driver.core.schemabuilder.SchemaBuilder.KeyCaching.ALL
import com.datastax.driver.core.schemabuilder.SchemaBuilder.rows
import com.linagora.openpaas.encrypted.cassandra.table.KeyStoreTable.{ID, KEY, TABLE_NAME, USERNAME}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraKeystoreModule {
  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Holds Keystore definition. Used to manage user public keys used for data encryption.")
    .options((options: Options) => options
      .caching(ALL, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement((statement: Create) => statement
      .addPartitionKey(USERNAME, text)
      .addClusteringColumn(ID, text)
      .addColumn(KEY, blob))
    .build
}
