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

import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.defaultCreateUserTableFunction;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.userTable;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresTable;
import org.apache.james.user.postgres.PostgresUserDataDefinition;
import org.jooq.CreateTableElementListStep;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.impl.SQLDataType;
import org.jooq.postgres.extensions.bindings.HstoreBinding;
import org.jooq.postgres.extensions.types.Hstore;

import com.google.common.annotations.VisibleForTesting;

public interface TMailPostgresUserDataDefinition {
    interface PostgresUserTable {
        Table<Record> TABLE_NAME = PostgresUserDataDefinition.PostgresUserTable.TABLE_NAME;

        Field<String> USERNAME = PostgresUserDataDefinition.PostgresUserTable.USERNAME;
        Field<Hstore> SETTINGS = DSL.field("settings", DefaultDataType.getDefaultDataType("hstore").asConvertedDataType(new HstoreBinding()));
        Field<UUID> SETTINGS_STATE = DSL.field("settings_state", SQLDataType.UUID);
        Field<UUID> RATE_LIMITING_PLAN_ID = DSL.field("rate_limiting_plan_id", SQLDataType.UUID);

        static PostgresTable userTable(PostgresTable.CreateTableFunction createUserTableFunction) {
            return PostgresTable.name(TABLE_NAME.getName())
                .createTableStep(createUserTableFunction)
                .disableRowLevelSecurity()
                .build();
        }

        static PostgresTable.CreateTableFunction defaultCreateUserTableFunction() {
            return PostgresUserTable::defaultUserTableStatement;
        }

        static CreateTableElementListStep defaultUserTableStatement(DSLContext dsl, String tableName) {
            return dsl.createTableIfNotExists(tableName)
                .column(PostgresUserDataDefinition.PostgresUserTable.USERNAME)
                .column(PostgresUserDataDefinition.PostgresUserTable.HASHED_PASSWORD)
                .column(PostgresUserDataDefinition.PostgresUserTable.ALGORITHM)
                .column(PostgresUserDataDefinition.PostgresUserTable.AUTHORIZED_USERS)
                .column(PostgresUserDataDefinition.PostgresUserTable.DELEGATED_USERS)
                .column(SETTINGS)
                .column(SETTINGS_STATE)
                .column(RATE_LIMITING_PLAN_ID)
                .constraint(DSL.constraint(PostgresUserDataDefinition.PostgresUserTable.USERNAME_PRIMARY_KEY).primaryKey(USERNAME));
        }

        PostgresIndex RATE_LIMITING_PLAN_ID_INDEX = PostgresIndex.name("index_user_rate_limiting_plan_id")
            .createIndexStep((dslContext, indexName) -> dslContext.createIndexIfNotExists(indexName)
            .on(TABLE_NAME, RATE_LIMITING_PLAN_ID));
    }

    static PostgresDataDefinition userDataDefinition(PostgresTable.CreateTableFunction createUserTableFunction) {
        return PostgresDataDefinition.builder()
            .addTable(userTable(createUserTableFunction))
            .addIndex(PostgresUserTable.RATE_LIMITING_PLAN_ID_INDEX)
            .build();
    }

    @VisibleForTesting
    PostgresDataDefinition MODULE = userDataDefinition(defaultCreateUserTableFunction());
}
