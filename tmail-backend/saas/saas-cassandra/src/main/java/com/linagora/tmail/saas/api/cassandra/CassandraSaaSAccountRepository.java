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

import static com.datastax.oss.driver.api.core.type.codec.TypeCodecs.TEXT;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.SAAS_PLAN;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.TABLE_NAME;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.USER;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public class CassandraSaaSAccountRepository implements SaaSAccountRepository {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertPlanStatement;
    private final PreparedStatement selectPlanStatement;

    @Inject
    public CassandraSaaSAccountRepository(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertPlanStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(SAAS_PLAN, bindMarker(SAAS_PLAN))
            .build());
        this.selectPlanStatement = session.prepare(selectFrom(TABLE_NAME)
            .column(SAAS_PLAN)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Username username) {
        return Mono.from(executor.executeSingleRow(selectPlanStatement.bind()
                .setString(USER, username.asString())))
            .mapNotNull(row -> row.getString(SAAS_PLAN))
            .map(saasPlanString -> new SaaSAccount(new SaaSPlan(saasPlanString)));
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Username username, SaaSAccount saaSAccount) {
        return Mono.from(executor.executeVoid(insertPlanStatement.bind()
            .set(USER, username.asString(), TEXT)
            .set(SAAS_PLAN, saaSAccount.saaSPlan().value(), TEXT)));
    }
}
