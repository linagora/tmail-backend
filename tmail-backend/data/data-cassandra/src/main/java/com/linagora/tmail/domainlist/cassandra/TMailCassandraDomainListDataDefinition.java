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
 *******************************************************************/

package com.linagora.tmail.domainlist.cassandra;

import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_RECEIVED_PER_DAYS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_RECEIVED_PER_HOURS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_RECEIVED_PER_MINUTE;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_SENT_PER_DAYS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_SENT_PER_HOURS;
import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.MAILS_SENT_PER_MINUTE;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.backends.cassandra.utils.CassandraConstants;
import org.apache.james.domainlist.cassandra.tables.CassandraDomainsTable;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataTypes;

public interface TMailCassandraDomainListDataDefinition {
    String TABLE_NAME = CassandraDomainsTable.TABLE_NAME;
    CqlIdentifier DOMAIN = CassandraDomainsTable.DOMAIN;
    CqlIdentifier ACTIVATED = CqlIdentifier.fromCql("activated");
    CqlIdentifier CAN_UPGRADE = CqlIdentifier.fromCql("can_upgrade");
    CqlIdentifier IS_PAYING = CqlIdentifier.fromCql("is_paying");
    CqlIdentifier SIGNATURE_TEXT_PER_LANGUAGE = CqlIdentifier.fromCql("signature_text_per_language");
    CqlIdentifier SIGNATURE_HTML_PER_LANGUAGE = CqlIdentifier.fromCql("signature_html_per_language");

    CassandraDataDefinition MODULE = CassandraDataDefinition.table(TABLE_NAME)
        .comment("Holds domains this TMail server is operating on.")
        .options(options -> options
            .withCaching(true, rows(CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION)))
        .statement(statement -> types -> statement
            .withPartitionKey(DOMAIN, DataTypes.TEXT)
            .withColumn(MAILS_SENT_PER_MINUTE, DataTypes.BIGINT)
            .withColumn(MAILS_SENT_PER_HOURS, DataTypes.BIGINT)
            .withColumn(MAILS_SENT_PER_DAYS, DataTypes.BIGINT)
            .withColumn(MAILS_RECEIVED_PER_MINUTE, DataTypes.BIGINT)
            .withColumn(MAILS_RECEIVED_PER_HOURS, DataTypes.BIGINT)
            .withColumn(MAILS_RECEIVED_PER_DAYS, DataTypes.BIGINT)
            .withColumn(ACTIVATED, DataTypes.BOOLEAN)
            .withColumn(CAN_UPGRADE, DataTypes.BOOLEAN)
            .withColumn(IS_PAYING, DataTypes.BOOLEAN)
            .withColumn(SIGNATURE_TEXT_PER_LANGUAGE, DataTypes.frozenMapOf(DataTypes.TEXT, DataTypes.TEXT))
            .withColumn(SIGNATURE_HTML_PER_LANGUAGE, DataTypes.frozenMapOf(DataTypes.TEXT, DataTypes.TEXT)))
        .build();
}
