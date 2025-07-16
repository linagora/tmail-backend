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

package com.linagora.tmail.user.postgres;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresTable;
import org.apache.james.user.postgres.PostgresUserDataDefinition;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;

public interface TMailPostgresUserDataDefinition {
    interface PostgresUserTable {
        Table<Record> TABLE_NAME = PostgresUserDataDefinition.PostgresUserTable.TABLE_NAME;

        Field<String> USERNAME = PostgresUserDataDefinition.PostgresUserTable.USERNAME;

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(PostgresUserDataDefinition.PostgresUserTable.USERNAME)
                .column(PostgresUserDataDefinition.PostgresUserTable.HASHED_PASSWORD)
                .column(PostgresUserDataDefinition.PostgresUserTable.ALGORITHM)
                .column(PostgresUserDataDefinition.PostgresUserTable.AUTHORIZED_USERS)
                .column(PostgresUserDataDefinition.PostgresUserTable.DELEGATED_USERS)
                .constraint(DSL.constraint(PostgresUserDataDefinition.PostgresUserTable.USERNAME_PRIMARY_KEY).primaryKey(USERNAME))))
            .disableRowLevelSecurity()
            .build();
    }

    PostgresDataDefinition MODULE = PostgresDataDefinition.builder()
        .addTable(PostgresUserTable.TABLE)
        .build();
}
