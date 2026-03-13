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
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.CAN_UPGRADE;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.IS_PAYING;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.TABLE_NAME;
import static com.linagora.tmail.saas.api.cassandra.CassandraSaaSDataDefinition.USER;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodecs;
import com.linagora.tmail.domainlist.cassandra.TMailCassandraDomainListDataDefinition;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class CassandraSaaSAccountRepository implements SaaSAccountRepository {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement insertPlanStatement;
    private final PreparedStatement selectPlanStatement;
    private final PreparedStatement clearSaaSAccountStatement;
    private final PreparedStatement insertDomainPlanStatement;
    private final PreparedStatement selectDomainPlanStatement;
    private final PreparedStatement clearDomainSaaSAccountStatement;

    private static final CqlIdentifier DOMAIN_ID = TMailCassandraDomainListDataDefinition.DOMAIN;
    private static final CqlIdentifier DOMAIN_CAN_UPGRADE = TMailCassandraDomainListDataDefinition.CAN_UPGRADE;
    private static final CqlIdentifier DOMAIN_IS_PAYING = TMailCassandraDomainListDataDefinition.IS_PAYING;
    private static final String DOMAIN_TABLE = TMailCassandraDomainListDataDefinition.TABLE_NAME;

    @Inject
    public CassandraSaaSAccountRepository(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        this.insertPlanStatement = session.prepare(insertInto(TABLE_NAME)
            .value(USER, bindMarker(USER))
            .value(CAN_UPGRADE, bindMarker(CAN_UPGRADE))
            .value(IS_PAYING, bindMarker(IS_PAYING))
            .build());
        this.selectPlanStatement = session.prepare(selectFrom(TABLE_NAME)
            .columns(IS_PAYING, CAN_UPGRADE)
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());
        this.clearSaaSAccountStatement = session.prepare(update(TABLE_NAME)
            .setColumn(CAN_UPGRADE, bindMarker(CAN_UPGRADE))
            .setColumn(IS_PAYING, bindMarker(IS_PAYING))
            .whereColumn(USER).isEqualTo(bindMarker(USER))
            .build());
        this.insertDomainPlanStatement = session.prepare(insertInto(DOMAIN_TABLE)
            .value(DOMAIN_ID, bindMarker(DOMAIN_ID))
            .value(DOMAIN_CAN_UPGRADE, bindMarker(DOMAIN_CAN_UPGRADE))
            .value(DOMAIN_IS_PAYING, bindMarker(DOMAIN_IS_PAYING))
            .build());
        this.selectDomainPlanStatement = session.prepare(selectFrom(DOMAIN_TABLE)
            .columns(DOMAIN_CAN_UPGRADE, DOMAIN_IS_PAYING)
            .whereColumn(DOMAIN_ID).isEqualTo(bindMarker(DOMAIN_ID))
            .build());
        this.clearDomainSaaSAccountStatement = session.prepare(update(DOMAIN_TABLE)
            .setColumn(DOMAIN_CAN_UPGRADE, bindMarker(DOMAIN_CAN_UPGRADE))
            .setColumn(DOMAIN_IS_PAYING, bindMarker(DOMAIN_IS_PAYING))
            .whereColumn(DOMAIN_ID).isEqualTo(bindMarker(DOMAIN_ID))
            .build());
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Username username) {
        return getUserSaaSAccount(username)
            .switchIfEmpty(Mono.defer(() -> username.getDomainPart()
                .map(domain -> Mono.from(getSaaSAccount(domain)))
                .orElse(Mono.empty())))
            .switchIfEmpty(Mono.just(SaaSAccount.DEFAULT));
    }

    private Mono<SaaSAccount> getUserSaaSAccount(Username username) {
        return Mono.from(executor.executeSingleRow(selectPlanStatement.bind()
                .setString(USER, username.asString())))
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
    public Publisher<Void> upsertSaasAccount(Username username, SaaSAccount saaSAccount) {
        return Mono.from(executor.executeVoid(insertPlanStatement.bind()
            .set(USER, username.asString(), TEXT)
            .set(CAN_UPGRADE, saaSAccount.canUpgrade(), BOOLEAN)
            .set(IS_PAYING, saaSAccount.isPaying(), BOOLEAN)));
    }

    @Override
    public Publisher<Void> deleteSaaSAccount(Username username) {
        return Mono.from(executor.executeVoid(clearSaaSAccountStatement.bind()
            .set(USER, username.asString(), TypeCodecs.TEXT)
            .setToNull(CAN_UPGRADE)
            .setToNull(IS_PAYING)));
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Domain domain) {
        return Mono.from(executor.executeSingleRow(selectDomainPlanStatement.bind()
                .setString(DOMAIN_ID, domain.asString())))
            .flatMap(row -> {
                Boolean canUpgrade = row.get(DOMAIN_CAN_UPGRADE, Boolean.class);
                Boolean isPaying = row.get(DOMAIN_IS_PAYING, Boolean.class);
                if (canUpgrade == null && isPaying == null) {
                    return Mono.<SaaSAccount>empty();
                }
                return Mono.just(new SaaSAccount(
                    Optional.ofNullable(canUpgrade).orElse(SaaSAccount.DEFAULT.canUpgrade()),
                    Optional.ofNullable(isPaying).orElse(SaaSAccount.DEFAULT.isPaying())));
            });
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Domain domain, SaaSAccount saaSAccount) {
        return Mono.from(executor.executeVoid(insertDomainPlanStatement.bind()
            .set(DOMAIN_ID, domain.asString(), TEXT)
            .set(DOMAIN_CAN_UPGRADE, saaSAccount.canUpgrade(), BOOLEAN)
            .set(DOMAIN_IS_PAYING, saaSAccount.isPaying(), BOOLEAN)));
    }

    @Override
    public Publisher<Void> deleteSaaSAccount(Domain domain) {
        return Mono.from(executor.executeVoid(clearDomainSaaSAccountStatement.bind()
            .set(DOMAIN_ID, domain.asString(), TypeCodecs.TEXT)
            .setToNull(DOMAIN_CAN_UPGRADE)
            .setToNull(DOMAIN_IS_PAYING)));
    }
}
