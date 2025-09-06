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

package com.linagora.tmail.saas.api.postgres;

import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.defaultUserTableStatement;
import static com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.userDataDefinition;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import com.google.common.annotations.VisibleForTesting;
import com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition;

public interface PostgresSaaSDataDefinition {
    Table<Record> TABLE_NAME = TMailPostgresUserDataDefinition.PostgresUserTable.TABLE_NAME;
    Field<String> USERNAME = TMailPostgresUserDataDefinition.PostgresUserTable.USERNAME;
    Field<Boolean> CAN_UPGRADE = DSL.field("can_upgrade", SQLDataType.BOOLEAN);
    Field<Boolean> IS_PAYING = DSL.field("is_paying", SQLDataType.BOOLEAN);
    Field<JSONB> RATE_LIMITING = DSL.field("rate_limiting", SQLDataType.JSONB);

    static PostgresTable.CreateTableFunction userTableWithSaaSSupport() {
        return (DSLContext dsl, String tableName) -> defaultUserTableStatement(dsl, tableName)
            .column(CAN_UPGRADE)
            .column(IS_PAYING)
            .column(RATE_LIMITING);
    }

    @VisibleForTesting
    PostgresDataDefinition MODULE = userDataDefinition(userTableWithSaaSSupport());
}
