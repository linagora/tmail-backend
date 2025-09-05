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

import static com.datastax.oss.driver.api.core.type.codec.TypeCodecs.BOOLEAN;
import static com.datastax.oss.driver.api.core.type.codec.TypeCodecs.TEXT;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.CAN_UPGRADE;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.IS_PAYING;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.RATE_LIMITING;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.TABLE_NAME;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.USER;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.RateLimitingDefinition;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class CassandraSaaSAccountRepository implements SaaSAccountRepository {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertPlanStatement;
    private final PreparedStatement selectPlanStatement;
    private final ObjectMapper objectMapper;

    @Inject
    public CassandraSaaSAccountRepository(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertPlanStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(CAN_UPGRADE, bindMarker(CAN_UPGRADE))
            .value(IS_PAYING, bindMarker(IS_PAYING))
            .value(RATE_LIMITING, bindMarker(RATE_LIMITING))
            .build());
        this.selectPlanStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(IS_PAYING, CAN_UPGRADE, RATE_LIMITING)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Username username) {
        return Mono.from(executor.executeSingleRow(selectPlanStatement.bind()
                .setString(USER, username.asString())))
            .mapNotNull(row -> new SaaSAccount(row.getBoolean(CAN_UPGRADE), row.getBoolean(IS_PAYING), getRateLimiting(row)))
            .switchIfEmpty(Mono.just(SaaSAccount.DEFAULT));
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Username username, SaaSAccount saaSAccount) {
        return Mono.from(executor.executeVoid(insertPlanStatement.bind()
            .set(USER, username.asString(), TEXT)
            .set(CAN_UPGRADE, saaSAccount.canUpgrade(), BOOLEAN)
            .set(IS_PAYING, saaSAccount.isPaying(), BOOLEAN)
            .set(RATE_LIMITING, asJson(saaSAccount.rateLimiting()), TEXT)));
    }

    private RateLimitingDefinition getRateLimiting(Row row) {
        return Optional.ofNullable(row.getString(RATE_LIMITING))
            .map(rateLimitingJson -> {
                try {
                    return objectMapper.readValue(rateLimitingJson, RateLimitingDefinition.class);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to deserialize SaaS rate limiting: " + rateLimitingJson, e);
                }
            })
            .orElse(RateLimitingDefinition.UNLIMITED);
    }

    private String asJson(RateLimitingDefinition rateLimitingDefinition) {
        try {
            return objectMapper.writeValueAsString(rateLimitingDefinition);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize SaaS rate limiting: " + rateLimitingDefinition, e);
        }
    }
}
