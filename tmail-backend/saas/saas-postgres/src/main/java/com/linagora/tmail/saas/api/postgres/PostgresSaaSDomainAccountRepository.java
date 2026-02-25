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

import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.CAN_UPGRADE;
import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.DOMAIN;
import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.IS_PAYING;
import static com.linagora.tmail.domainlist.postgres.TMailPostgresDomainDataDefinition.PostgresDomainTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.api.SaaSDomainAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class PostgresSaaSDomainAccountRepository implements SaaSDomainAccountRepository {
    private final PostgresExecutor executor;

    @Inject
    public PostgresSaaSDomainAccountRepository(PostgresExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Publisher<SaaSAccount> getSaaSDomainAccount(Domain domain) {
        return Mono.from(executor.executeRow(dsl -> Mono.from(dsl.select(CAN_UPGRADE, IS_PAYING)
                .from(TABLE_NAME)
                .where(DOMAIN.eq(domain.asString())))))
            .flatMap(row -> {
                Boolean canUpgrade = row.get(CAN_UPGRADE);
                Boolean isPaying = row.get(IS_PAYING);
                if (canUpgrade == null && isPaying == null) {
                    return Mono.empty();
                }
                return Mono.just(new SaaSAccount(
                    canUpgrade != null ? canUpgrade : SaaSAccount.DEFAULT.canUpgrade(),
                    isPaying != null ? isPaying : SaaSAccount.DEFAULT.isPaying()));
            });
    }

    @Override
    public Publisher<Void> upsertSaasDomainAccount(Domain domain, SaaSAccount saaSAccount) {
        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(DOMAIN, domain.asString())
            .set(CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(IS_PAYING, saaSAccount.isPaying())
            .onConflict(DOMAIN)
            .doUpdate()
            .set(CAN_UPGRADE, saaSAccount.canUpgrade())
            .set(IS_PAYING, saaSAccount.isPaying())));
    }

    @Override
    public Publisher<Void> deleteSaaSDomainAccount(Domain domain) {
        return executor.executeVoid(dsl -> Mono.from(dsl.update(TABLE_NAME)
            .set(CAN_UPGRADE, (Boolean) null)
            .set(IS_PAYING, (Boolean) null)
            .where(DOMAIN.eq(domain.asString()))));
    }
}
