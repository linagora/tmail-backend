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
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.jooq.Record;

import com.linagora.tmail.mailbox.quota.QuotaSum;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaSumDao implements QuotaSumDao {
    private final PostgresExecutor postgresExecutor;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public PostgresQuotaSumDao(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor, QuotaRootResolver quotaRootResolver) {
        this.postgresExecutor = postgresExecutor;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public Mono<QuotaSum> globalUsage() {
        return mailboxCurrentValues()
            .filter(value -> value.getCurrentValue() > 0)
            .reduce(QuotaSum.ZERO, PostgresQuotaSumDao::accumulate);
    }

    @Override
    public Mono<QuotaSum> domainUsage(Domain domain) {
        return mailboxCurrentValues()
            .filter(value -> value.getCurrentValue() > 0)
            .filterWhen(value -> matchesDomain(value, domain))
            .reduce(QuotaSum.ZERO, PostgresQuotaSumDao::accumulate);
    }

    private Flux<QuotaCurrentValue> mailboxCurrentValues() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(COMPONENT.eq(QuotaComponent.MAILBOX.getValue()))))
            .map(this::toCurrentValue);
    }

    private Mono<Boolean> matchesDomain(QuotaCurrentValue value, Domain domain) {
        return Mono.fromCallable(() -> quotaRootResolver.associatedUsername(quotaRootResolver.fromString(value.getIdentifier())))
            .map(Username::getDomainPart)
            .map(maybeDomain -> maybeDomain.map(domain::equals).orElse(false));
    }

    private QuotaCurrentValue toCurrentValue(Record record) {
        return QuotaCurrentValue.builder()
            .quotaComponent(QuotaComponent.of(record.get(COMPONENT)))
            .identifier(record.get(IDENTIFIER))
            .quotaType(QuotaType.of(record.get(TYPE)))
            .currentValue(record.get(CURRENT_VALUE))
            .build();
    }

    private static QuotaSum accumulate(QuotaSum acc, QuotaCurrentValue value) {
        if (value.getQuotaType().equals(QuotaType.COUNT)) {
            return new QuotaSum(acc.count() + value.getCurrentValue(), acc.size());
        }
        if (value.getQuotaType().equals(QuotaType.SIZE)) {
            return new QuotaSum(acc.count(), acc.size() + value.getCurrentValue());
        }
        return acc;
    }
}
