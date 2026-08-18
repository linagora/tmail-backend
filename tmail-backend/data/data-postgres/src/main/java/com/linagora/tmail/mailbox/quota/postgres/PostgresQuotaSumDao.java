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
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaType;
import org.jooq.Record;

import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.model.QuotaSum;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresQuotaSumDao implements QuotaSumDao {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresQuotaSumDao(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public Mono<QuotaSum> globalUsage() {
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(TYPE, CURRENT_VALUE)
                .from(TABLE_NAME)
                .where(COMPONENT.eq(QuotaComponent.MAILBOX.getValue()))))
            .reduce(QuotaSum.ZERO, this::accumulatePositive);
    }

    @Override
    public Mono<QuotaSum> domainUsage(Domain domain) {
        String domainPattern = "%@" + domain.asString();
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.select(TYPE, CURRENT_VALUE)
                .from(TABLE_NAME)
                .where(COMPONENT.eq(QuotaComponent.MAILBOX.getValue()))
                .and(IDENTIFIER.like(domainPattern))))
            .reduce(QuotaSum.ZERO, this::accumulatePositive);
    }

    private QuotaSum accumulatePositive(QuotaSum sum, Record record) {
        QuotaType quotaType = QuotaType.of(record.get(TYPE));
        long value = Math.max(0L, record.get(CURRENT_VALUE));
        if (quotaType.equals(QuotaType.COUNT)) {
            return new QuotaSum(sum.count() + value, sum.size());
        }
        if (quotaType.equals(QuotaType.SIZE)) {
            return new QuotaSum(sum.count(), sum.size() + value);
        }
        return sum;
    }
}
