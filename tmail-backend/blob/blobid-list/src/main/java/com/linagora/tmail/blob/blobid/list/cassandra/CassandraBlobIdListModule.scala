package com.linagora.tmail.blob.blobid.list.cassandra

import com.datastax.driver.core.DataType.{blob, text}
import com.datastax.driver.core.schemabuilder.Create
import com.datastax.driver.core.schemabuilder.Create.Options
import com.datastax.driver.core.schemabuilder.SchemaBuilder.KeyCaching.ALL
import com.datastax.driver.core.schemabuilder.SchemaBuilder.rows
import BlobIdListTable.{BLOB_ID, TABLE_NAME}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraBlobIdListModule {
  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Holds BlobId list definition")
    .options((options: Options) => options
      .caching(ALL, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement((statement: Create) => statement
      .addPartitionKey(BLOB_ID, text))
    .build
}
