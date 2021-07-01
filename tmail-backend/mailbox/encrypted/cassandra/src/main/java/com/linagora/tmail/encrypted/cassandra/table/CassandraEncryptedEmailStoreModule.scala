package com.linagora.tmail.encrypted.cassandra.table

import com.datastax.driver.core.DataType.{cboolean, cint, frozenMap, text, timeuuid}
import com.datastax.driver.core.schemabuilder.Create
import com.datastax.driver.core.schemabuilder.Create.Options
import com.datastax.driver.core.schemabuilder.SchemaBuilder.KeyCaching.ALL
import com.datastax.driver.core.schemabuilder.SchemaBuilder.rows
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraEncryptedEmailStoreModule {
  val MODULE: CassandraModule = CassandraModule.table(EncryptedEmailTable.TABLE_NAME)
    .comment("Holds content of encrypted email in order to ease the JMAP display")
    .options((options: Options) => options
      .caching(ALL, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement((statement: Create) => statement
      .addPartitionKey(EncryptedEmailTable.MESSAGE_ID, timeuuid())
      .addColumn(EncryptedEmailTable.ENCRYPTED_PREVIEW, text())
      .addColumn(EncryptedEmailTable.ENCRYPTED_HTML, text())
      .addColumn(EncryptedEmailTable.HAS_ATTACHMENT, cboolean())
      .addColumn(EncryptedEmailTable.ENCRYPTED_ATTACHMENT_METADATA, text())
      .addColumn(EncryptedEmailTable.POSITION_BLOB_ID_MAPPING, frozenMap(cint(), text())))
    .build
}

