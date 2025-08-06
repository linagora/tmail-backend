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

package com.linagora.tmail.saas.api.cassandra;

import static com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.createUserTableDefinition;

import java.util.function.Function;

import org.apache.james.backends.cassandra.components.CassandraDataDefinition;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTable;
import com.datastax.oss.driver.api.querybuilder.schema.CreateTableStart;
import com.google.common.annotations.VisibleForTesting;
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition;

public interface CassandraSaaSDataDefinition {
    String TABLE_NAME = TMailCassandraUsersRepositoryDataDefinition.TABLE_NAME;
    CqlIdentifier USER = TMailCassandraUsersRepositoryDataDefinition.USER;
    CqlIdentifier SAAS_PLAN = CqlIdentifier.fromCql("saas_plan");

    @VisibleForTesting
    CassandraDataDefinition MODULE = createUserTableDefinition(userTableWithSaaSSupport());

    static Function<CreateTableStart, CreateTable> userTableWithSaaSSupport() {
        return statement -> TMailCassandraUsersRepositoryDataDefinition.defaultCreateUserTableStatement(statement)
            .withColumn(SAAS_PLAN, DataTypes.TEXT);
    }
}
