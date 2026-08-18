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

package com.linagora.tmail.mailbox.quota.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueTable.CURRENT_VALUE;
import static org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueTable.IDENTIFIER;
import static org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueTable.QUOTA_COMPONENT;
import static org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueTable.QUOTA_TYPE;
import static org.apache.james.backends.cassandra.components.CassandraQuotaCurrentValueTable.TABLE_NAME;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaType;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.model.QuotaSum;

import reactor.core.publisher.Mono;

public class CassandraQuotaSumDao implements QuotaSumDao {
    private final CassandraAsyncExecutor executor;
    private final PreparedStatement getAllQuotaCurrentValuesStatement;

    @Inject
    public CassandraQuotaSumDao(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);
        // ponytail: full scan of the quotaCurrentValue table filtered by component=MAILBOX.
        // This serves a rare analytic report need; per-domain filtering is done client-side
        // as the partition key (identifier) does not allow efficient suffix matching.
        this.getAllQuotaCurrentValuesStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .build());
    }

    @Override
    public Mono<QuotaSum> globalUsage() {
        return executor.executeRows(getAllQuotaCurrentValuesStatement.bind())
            .filter(this::isMailboxQuota)
            .reduce(QuotaSum.ZERO, this::accumulatePositive);
    }

    @Override
    public Mono<QuotaSum> domainUsage(Domain domain) {
        String suffix = "@" + domain.asString();
        return executor.executeRows(getAllQuotaCurrentValuesStatement.bind())
            .filter(this::isMailboxQuota)
            .filter(row -> matchesDomain(row, suffix))
            .reduce(QuotaSum.ZERO, this::accumulatePositive);
    }

    private boolean isMailboxQuota(Row row) {
        return QuotaComponent.of(row.get(QUOTA_COMPONENT, String.class)).equals(QuotaComponent.MAILBOX);
    }

    private boolean matchesDomain(Row row, String suffix) {
        String identifier = row.get(IDENTIFIER, String.class);
        return identifier != null && identifier.endsWith(suffix);
    }

    private QuotaSum accumulatePositive(QuotaSum sum, Row row) {
        QuotaType quotaType = QuotaType.of(row.get(QUOTA_TYPE, String.class));
        long value = Math.max(0L, row.get(CURRENT_VALUE, Long.class));
        if (quotaType.equals(QuotaType.COUNT)) {
            return new QuotaSum(sum.count() + value, sum.size());
        }
        if (quotaType.equals(QuotaType.SIZE)) {
            return new QuotaSum(sum.count(), sum.size() + value);
        }
        return sum;
    }
}
