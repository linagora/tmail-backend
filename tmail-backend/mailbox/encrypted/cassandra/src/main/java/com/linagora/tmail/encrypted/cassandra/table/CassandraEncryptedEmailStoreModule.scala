/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.encrypted.cassandra.table

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION

object CassandraEncryptedEmailStoreModule {
  val MODULE: CassandraDataDefinition = CassandraDataDefinition.table(EncryptedEmailTable.TABLE_NAME)
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

