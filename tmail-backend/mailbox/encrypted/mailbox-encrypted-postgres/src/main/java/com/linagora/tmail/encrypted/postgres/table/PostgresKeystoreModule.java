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

package com.linagora.tmail.encrypted.postgres.table;

import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.TABLE;
import static com.linagora.tmail.encrypted.postgres.table.PostgresKeystoreModule.PostgresKeystoreTable.USERNAME_INDEX;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresKeystoreModule {
    interface PostgresKeystoreTable {
        Table<Record> TABLE_NAME = DSL.table("keystore");

        Field<String> USERNAME = DSL.field("username", SQLDataType.VARCHAR.notNull());
        Field<String> ID = DSL.field("id", SQLDataType.VARCHAR.notNull());
        Field<byte[]> KEY = DSL.field("plan_name", SQLDataType.BLOB.notNull());

        Name PK_CONSTRAINT_NAME = DSL.name("keystore_pkey");

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(USERNAME)
                .column(ID)
                .column(KEY)
                .constraint(DSL.constraint(PK_CONSTRAINT_NAME).primaryKey(USERNAME, ID))))
            .supportsRowLevelSecurity()
            .build();

        PostgresIndex USERNAME_INDEX = PostgresIndex.name("keystore_username_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, USERNAME));
    }

    PostgresDataDefinition MODULE = PostgresDataDefinition.builder()
        .addTable(TABLE)
        .addIndex(USERNAME_INDEX)
        .build();
}
