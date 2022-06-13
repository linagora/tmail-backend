package com.linagora.tmail.blob.blobid.list.cassandra

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows
import com.linagora.tmail.blob.blobid.list.cassandra.BlobIdListTable.{BLOB_ID, TABLE_NAME}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraBlobIdListModule {
  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Holds BlobId list definition")
    .options(options => options
      .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION))
      .withLZ4Compression(8, 1.0))
    .statement(statement => types => statement
      .withPartitionKey(BLOB_ID, DataTypes.TEXT))
    .build
}
