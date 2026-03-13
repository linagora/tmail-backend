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

import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.CAN_UPGRADE;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.IS_PAYING;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.TABLE_NAME;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.USERNAME;

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition;
import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class PostgresSaaSAccountRepository implements SaaSAccountRepository {

    private final PostgresExecutor executor;

    @Inject
    public PostgresSaaSAccountRepository(PostgresExecutor executor) {
        this.executor = executor;
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
        return Mono.from(executor.executeRow(dsl -> Mono.from(dsl.select(CAN_UPGRADE, IS_PAYING)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString())))))
            .flatMap(row -> {
                Boolean canUpgrade = row.get(CAN_UPGRADE);
                Boolean isPaying = row.get(IS_PAYING);
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
        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(IS_PAYING, saaSAccount.isPaying())
            .onConflict(USERNAME)
            .doUpdate()
            .set(CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(IS_PAYING, saaSAccount.isPaying())));
    }

    @Override
    public Publisher<Void> deleteSaaSAccount(Username username) {
        return executor.executeVoid(dsl -> Mono.from(dsl.update(TABLE_NAME)
            .set(CAN_UPGRADE, (Boolean) null)
            .set(IS_PAYING, (Boolean) null)
            .where(USERNAME.eq(username.asString()))));
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Domain domain) {
        return Mono.from(executor.executeRow(dsl -> Mono.from(dsl
                .select(TMailPostgresDomainDataDefinition.PostgresDomainTable.CAN_UPGRADE,
                    TMailPostgresDomainDataDefinition.PostgresDomainTable.IS_PAYING)
                .from(TMailPostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
                .where(TMailPostgresDomainDataDefinition.PostgresDomainTable.DOMAIN.eq(domain.asString())))))
            .flatMap(row -> {
                Boolean canUpgrade = row.get(TMailPostgresDomainDataDefinition.PostgresDomainTable.CAN_UPGRADE);
                Boolean isPaying = row.get(TMailPostgresDomainDataDefinition.PostgresDomainTable.IS_PAYING);
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
        return executor.executeVoid(dsl -> Mono.from(dsl
            .insertInto(TMailPostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.DOMAIN, domain.asString())
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.IS_PAYING, saaSAccount.isPaying())
            .onConflict(TMailPostgresDomainDataDefinition.PostgresDomainTable.DOMAIN)
            .doUpdate()
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.IS_PAYING, saaSAccount.isPaying())));
    }

    @Override
    public Publisher<Void> deleteSaaSAccount(Domain domain) {
        return executor.executeVoid(dsl -> Mono.from(dsl
            .update(TMailPostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME)
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.CAN_UPGRADE, (Boolean) null)
            .set(TMailPostgresDomainDataDefinition.PostgresDomainTable.IS_PAYING, (Boolean) null)
            .where(TMailPostgresDomainDataDefinition.PostgresDomainTable.DOMAIN.eq(domain.asString()))));
    }
}
