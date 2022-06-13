package com.linagora.tmail.encrypted.cassandra.table

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraEncryptedEmailStoreModule {
  val MODULE: CassandraModule = CassandraModule.table(EncryptedEmailTable.TABLE_NAME)
    .comment("Holds content of encrypted email in order to ease the JMAP display")
    .options(options => options
      .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement(statement => types => statement
      .withPartitionKey(EncryptedEmailTable.MESSAGE_ID, DataTypes.TIMEUUID)
      .withColumn(EncryptedEmailTable.ENCRYPTED_PREVIEW, DataTypes.TEXT)
      .withColumn(EncryptedEmailTable.ENCRYPTED_HTML, DataTypes.TEXT)
      .withColumn(EncryptedEmailTable.HAS_ATTACHMENT, DataTypes.BOOLEAN)
      .withColumn(EncryptedEmailTable.ENCRYPTED_ATTACHMENT_METADATA, DataTypes.TEXT)
      .withColumn(EncryptedEmailTable.POSITION_BLOB_ID_MAPPING, DataTypes.frozenMapOf(DataTypes.INT, DataTypes.TEXT)))
    .build
}

