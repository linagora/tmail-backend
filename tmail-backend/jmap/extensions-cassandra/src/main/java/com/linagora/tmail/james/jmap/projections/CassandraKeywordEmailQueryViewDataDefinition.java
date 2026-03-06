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

package com.linagora.tmail.james.jmap.projections;

import static com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.DESC;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.KEYWORD;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.MESSAGE_ID;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.RECEIVED_AT;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.TABLE_NAME;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.THREAD_ID;
import static com.linagora.tmail.james.jmap.projections.table.CassandraKeywordEmailQueryViewTable.USERNAME;
import static org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;

import com.datastax.oss.driver.api.core.type.DataTypes;

public interface CassandraKeywordEmailQueryViewDataDefinition {
    CassandraDataDefinition MODULE = CassandraDataDefinition.table(TABLE_NAME)
        .comment("Store keyword-based email query projections.")
        .options(options -> options
            .withClusteringOrder(RECEIVED_AT, DESC)
            .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(USERNAME, DataTypes.TEXT)
            .withPartitionKey(KEYWORD, DataTypes.TEXT)
            .withClusteringColumn(RECEIVED_AT, DataTypes.TIMESTAMP)
            .withClusteringColumn(MESSAGE_ID, DataTypes.UUID)
            .withColumn(THREAD_ID, DataTypes.UUID))
        .build();
}
