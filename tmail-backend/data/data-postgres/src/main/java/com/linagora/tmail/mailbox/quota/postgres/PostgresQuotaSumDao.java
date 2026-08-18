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

package com.linagora.tmail.mailbox.quota.postgres;

import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaCurrentValueTable.COMPONENT;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaCurrentValueTable.CURRENT_VALUE;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaCurrentValueTable.IDENTIFIER;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaCurrentValueTable.TABLE_NAME;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaCurrentValueTable.TYPE;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;
import org.jooq.Record;

import com.linagora.tmail.mailbox.quota.QuotaSum;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaSumDao extends QuotaSumDao {
    private static final QuotaComponent MAILBOX_COMPONENT = QuotaComponent.MAILBOX;

    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresQuotaSumDao(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor,
                               UsersRepository usersRepository,
                               UserQuotaRootResolver userQuotaRootResolver,
                               CurrentQuotaManager currentQuotaManager) {
        super(usersRepository, userQuotaRootResolver, currentQuotaManager);
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public Mono<QuotaSum> globalUsage() {
        return mailboxCurrentValues()
            .filter(value -> value.getCurrentValue() > 0)
            .reduce(QuotaSum.ZERO, QuotaSum::accumulate);
    }

    private Flux<QuotaCurrentValue> mailboxCurrentValues() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(COMPONENT.eq(MAILBOX_COMPONENT.getValue()))))
            .map(this::toCurrentValue);
    }

    private QuotaCurrentValue toCurrentValue(Record record) {
        return QuotaCurrentValue.builder()
            .quotaComponent(QuotaComponent.of(record.get(COMPONENT)))
            .identifier(record.get(IDENTIFIER))
            .quotaType(QuotaType.of(record.get(TYPE)))
            .currentValue(record.get(CURRENT_VALUE))
            .build();
    }
}
