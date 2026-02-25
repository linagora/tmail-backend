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
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.update;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.CAN_UPGRADE;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.DOMAIN;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.IS_PAYING;
import static com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition.TABLE_NAME;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class CassandraSaaSDomainAccountRepository implements SaaSDomainAccountRepository {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement clearStatement;

    @Inject
    public CassandraSaaSDomainAccountRepository(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(DOMAIN, bindMarker(DOMAIN))
            .value(CAN_UPGRADE, bindMarker(CAN_UPGRADE))
            .value(IS_PAYING, bindMarker(IS_PAYING))
            .build());
        this.selectStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(CAN_UPGRADE, IS_PAYING)
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .build());
        this.clearStatement = session.prepare(update(TABLE_NAME)
            .setColumn(CAN_UPGRADE, bindMarker(CAN_UPGRADE))
            .setColumn(IS_PAYING, bindMarker(IS_PAYING))
            .whereColumn(DOMAIN).isEqualTo(bindMarker(DOMAIN))
            .build());
    }

    @Override
    public Publisher<SaaSAccount> getSaaSDomainAccount(Domain domain) {
        return Mono.from(executor.executeSingleRow(selectStatement.bind()
                .setString(DOMAIN, domain.asString())))
            .flatMap(row -> {
                Boolean canUpgrade = row.get(CAN_UPGRADE, Boolean.class);
                Boolean isPaying = row.get(IS_PAYING, Boolean.class);
                if (canUpgrade == null && isPaying == null) {
                    return Mono.empty();
                }
                return Mono.just(new SaaSAccount(
                    Optional.ofNullable(canUpgrade).orElse(SaaSAccount.DEFAULT.canUpgrade()),
                    Optional.ofNullable(isPaying).orElse(SaaSAccount.DEFAULT.isPaying())));
            });
    }

    @Override
    public Publisher<Void> upsertSaasDomainAccount(Domain domain, SaaSAccount saaSAccount) {
        return Mono.from(executor.executeVoid(insertStatement.bind()
            .set(DOMAIN, domain.asString(), TEXT)
            .set(CAN_UPGRADE, saaSAccount.canUpgrade(), BOOLEAN)
            .set(IS_PAYING, saaSAccount.isPaying(), BOOLEAN)));
    }

    @Override
    public Publisher<Void> deleteSaaSDomainAccount(Domain domain) {
        return Mono.from(executor.executeVoid(clearStatement.bind()
            .set(DOMAIN, domain.asString(), TEXT)
            .setToNull(CAN_UPGRADE)
            .setToNull(IS_PAYING)));
    }
}
