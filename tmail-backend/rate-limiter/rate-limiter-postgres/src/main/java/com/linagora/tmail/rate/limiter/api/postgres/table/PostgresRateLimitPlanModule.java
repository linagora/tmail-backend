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

package com.linagora.tmail.rate.limiter.api.postgres.table;

import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.PLAN_ID_INDEX;
import static com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule.PostgresRateLimitPlanTable.TABLE;

import java.util.UUID;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresIndex;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresRateLimitPlanModule {
    interface PostgresRateLimitPlanTable {
        Table<Record> TABLE_NAME = DSL.table("rate_limit_plan");

        Field<UUID> PLAN_ID = DSL.field("plan_id", SQLDataType.UUID.notNull());
        Field<String> PLAN_NAME = DSL.field("plan_name", SQLDataType.VARCHAR.notNull());
        Field<String> OPERATION_LIMITATION_NAME = DSL.field("operation_limitation_name", SQLDataType.VARCHAR.notNull());
        Field<JSON> RATE_LIMITATIONS = DSL.field("rate_limitations", SQLDataType.JSON.notNull());

        PostgresTable TABLE = PostgresTable.name(TABLE_NAME.getName())
            .createTableStep(((dsl, tableName) -> dsl.createTableIfNotExists(tableName)
                .column(PLAN_ID)
                .column(PLAN_NAME)
                .column(OPERATION_LIMITATION_NAME)
                .column(RATE_LIMITATIONS)
                .primaryKey(PLAN_ID, OPERATION_LIMITATION_NAME)))
            .disableRowLevelSecurity()
            .build();

        PostgresIndex PLAN_ID_INDEX = PostgresIndex.name("rate_limit_plan_plan_id_index")
            .createIndexStep((dsl, indexName) -> dsl.createIndexIfNotExists(indexName)
                .on(TABLE_NAME, PLAN_ID));
    }

    PostgresDataDefinition MODULE = PostgresDataDefinition.builder()
        .addTable(TABLE)
        .addIndex(PLAN_ID_INDEX)
        .build();
}
