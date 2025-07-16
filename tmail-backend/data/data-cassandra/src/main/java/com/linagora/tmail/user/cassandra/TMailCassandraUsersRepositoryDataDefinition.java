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
 *                                                                  *
 *  This file was taken and adapted from the Apache James project.  *
 *                                                                  *
 *  https://james.apache.org                                        *
 *                                                                  *
 *  It was originally licensed under the Apache V2 license.         *
 *                                                                  *
 *  http://www.apache.org/licenses/LICENSE-2.0                      *
 ********************************************************************/

package com.linagora.tmail.user.cassandra;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;
import org.apache.james.user.cassandra.tables.CassandraUserTable;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataTypes;

public interface TMailCassandraUsersRepositoryDataDefinition {
    String TABLE_NAME = CassandraUserTable.TABLE_NAME;
    CqlIdentifier USER = CassandraUserTable.NAME;
    CqlIdentifier SETTINGS = CqlIdentifier.fromCql("settings");
    CqlIdentifier SETTINGS_STATE = CqlIdentifier.fromCql("settings_state");
    CqlIdentifier RATE_LIMITING_PLAN_ID = CqlIdentifier.fromCql("rate_limiting_plan_id");

    CassandraDataDefinition MODULE = CassandraDataDefinition.table(TABLE_NAME)
        .comment("Holds users and their associated data.")
        .statement(statement -> types -> statement
            .withPartitionKey(CassandraUserTable.NAME, DataTypes.TEXT)
            .withColumn(CassandraUserTable.REALNAME, DataTypes.TEXT)
            .withColumn(CassandraUserTable.PASSWORD, DataTypes.TEXT)
            .withColumn(CassandraUserTable.ALGORITHM, DataTypes.TEXT)
            .withColumn(CassandraUserTable.AUTHORIZED_USERS, DataTypes.setOf(DataTypes.TEXT))
            .withColumn(CassandraUserTable.DELEGATED_USERS, DataTypes.setOf(DataTypes.TEXT))
            .withColumn(SETTINGS, DataTypes.mapOf(DataTypes.TEXT, DataTypes.TEXT))
            .withColumn(SETTINGS_STATE, DataTypes.UUID)
            .withColumn(RATE_LIMITING_PLAN_ID, DataTypes.UUID))
        .build();
}
