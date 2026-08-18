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
 *  purpose. See the GNU Affero General Public License for          *
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
import org.apache.james.core.quota.QuotaCurrentValue;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.linagora.tmail.mailbox.quota.QuotaSum;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraQuotaSumDao implements QuotaSumDao {
    private static final int DEFAULT_CONCURRENCY = 16;
    private static final QuotaComponent MAILBOX_COMPONENT = QuotaComponent.MAILBOX;

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement getAllStatement;
    private final UsersRepository usersRepository;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final CurrentQuotaManager currentQuotaManager;

    @Inject
    public CassandraQuotaSumDao(CqlSession session,
                                UsersRepository usersRepository,
                                UserQuotaRootResolver userQuotaRootResolver,
                                CurrentQuotaManager currentQuotaManager) {
        this.executor = new CassandraAsyncExecutor(session);
        this.getAllStatement = session.prepare(selectFrom(TABLE_NAME).all().build());
        this.usersRepository = usersRepository;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.currentQuotaManager = currentQuotaManager;
    }

    @Override
    public Mono<QuotaSum> globalUsage() {
        return mailboxCurrentValues()
            .filter(value -> value.getCurrentValue() > 0)
            .reduce(QuotaSum.ZERO, QuotaSum::accumulate);
    }

    @Override
    public Mono<QuotaSum> domainUsage(Domain domain) {
        return Flux.from(usersRepository.listUsersOfADomainReactive(domain))
            .flatMap(user -> Mono.from(currentQuotaManager.getCurrentQuotas(userQuotaRootResolver.forUser(user))), DEFAULT_CONCURRENCY)
            .map(QuotaSum::from)
            .reduce(QuotaSum.ZERO, QuotaSum::sum);
    }

    private Flux<QuotaCurrentValue> mailboxCurrentValues() {
        return executor.executeRows(getAllStatement.bind())
            .map(this::toCurrentValue)
            .filter(value -> value.getQuotaComponent().equals(MAILBOX_COMPONENT));
    }

    private QuotaCurrentValue toCurrentValue(Row row) {
        return QuotaCurrentValue.builder()
            .quotaComponent(QuotaComponent.of(row.get(QUOTA_COMPONENT, String.class)))
            .identifier(row.get(IDENTIFIER, String.class))
            .quotaType(QuotaType.of(row.get(QUOTA_TYPE, String.class)))
            .currentValue(row.get(CURRENT_VALUE, Long.class))
            .build();
    }
}
