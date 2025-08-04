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

import org.apache.james.backends.postgres.PostgresTable;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

public interface PostgresSaaSDataDefinition {
    Field<String> SAAS_PLAN = DSL.field("saas_plan", SQLDataType.VARCHAR(50));

    static PostgresTable.CreateTableFunction userTableWithSaaSSupport() {
        return (DSLContext dsl, String tableName) -> defaultUserTableStatement(dsl, tableName)
            .column(SAAS_PLAN);
    }
}
