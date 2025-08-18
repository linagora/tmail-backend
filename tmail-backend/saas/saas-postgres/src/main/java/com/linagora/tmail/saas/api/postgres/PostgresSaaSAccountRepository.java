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

import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.SAAS_PLAN;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.TABLE_NAME;
import static com.linagora.tmail.saas.api.postgres.PostgresSaaSDataDefinition.USERNAME;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.reactivestreams.Publisher;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public class PostgresSaaSAccountRepository implements SaaSAccountRepository {
    private final PostgresExecutor executor;

    @Inject
    public PostgresSaaSAccountRepository(PostgresExecutor executor) {
        this.executor = executor;
    }

    @Override
    public Publisher<SaaSAccount> getSaaSAccount(Username username) {
        return Mono.from(executor.executeRow(dsl -> Mono.from(dsl.select(SAAS_PLAN)
                .from(TABLE_NAME)
                .where(USERNAME.eq(username.asString())))))
            .mapNotNull(record -> record.get(SAAS_PLAN))
            .map(saasPlan -> new SaaSAccount(new SaaSPlan(saasPlan)));
    }

    @Override
    public Publisher<Void> upsertSaasAccount(Username username, SaaSAccount saaSAccount) {
        return executor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(USERNAME, username.asString())
            .set(SAAS_PLAN, saaSAccount.saaSPlan().value())
            .onConflict(USERNAME)
            .doUpdate()
            .set(SAAS_PLAN, saaSAccount.saaSPlan().value())));
    }
}
