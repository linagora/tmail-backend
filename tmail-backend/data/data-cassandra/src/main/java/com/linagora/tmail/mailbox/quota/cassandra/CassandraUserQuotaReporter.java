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
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.IDENTIFIER;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_COMPONENT;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_LIMIT;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_SCOPE;
import static org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable.QUOTA_TYPE;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.components.CassandraQuotaLimitDao;
import org.apache.james.backends.cassandra.components.CassandraQuotaLimitTable;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.quota.QuotaCodec;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.model.ExtraQuotaSum;
import com.linagora.tmail.mailbox.quota.model.Limits;
import com.linagora.tmail.mailbox.quota.model.UserWithSpecificQuota;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class CassandraUserQuotaReporter implements UserQuotaReporter {
    private static final String GLOBAL_IDENTIFIER = "global";

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement getAllQuotaLimitsStatement;
    private final CassandraQuotaLimitDao cassandraQuotaLimitDao;

    @Inject
    public CassandraUserQuotaReporter(CqlSession session, CassandraQuotaLimitDao cassandraQuotaLimitDao) {
        this.executor = new CassandraAsyncExecutor(session);
        this.getAllQuotaLimitsStatement = session.prepare(selectFrom(CassandraQuotaLimitTable.TABLE_NAME)
            .all()
            .build());
        this.cassandraQuotaLimitDao = cassandraQuotaLimitDao;
    }

    @Override
    public Flux<UserWithSpecificQuota> usersWithSpecificQuota() {
        return getSpecificUsersQuota();
    }

    @Override
    public Mono<Long> usersWithSpecificQuotaCount() {
        return getSpecificUsersQuota()
            .count();
    }

    @Override
    public Mono<ExtraQuotaSum> usersExtraQuotaSum() {
        return getSpecificUsersQuota()
            .groupBy(userQuota -> userQuota.username().getDomainPart())
            .flatMap(this::calculateExtraQuota)
            .reduce(ExtraQuotaSum.NONE, ExtraQuotaSum::merge);
    }

    private Flux<UserWithSpecificQuota> getSpecificUsersQuota() {
        return executor.executeRows(getAllQuotaLimitsStatement.bind())
            .map(this::convertRowToModel)
            .filter(isUserScoped())
            .filter(isMailboxQuota())
            .groupBy(QuotaLimit::getIdentifier)
            .flatMap(this::toUserSpecificQuota);
    }

    private Mono<UserWithSpecificQuota> toUserSpecificQuota(GroupedFlux<String, QuotaLimit> limitsOfUser) {
        Username username = Username.of(limitsOfUser.key());

        return limitsOfUser
            .collectList()
            .map(quotaLimits -> {
                Map<QuotaType, Optional<Long>> map = quotaLimits.stream()
                    .collect(Collectors.toMap(QuotaLimit::getQuotaType, QuotaLimit::getQuotaLimit));
                return new Limits(
                    map.getOrDefault(QuotaType.SIZE, Optional.empty()).flatMap(QuotaCodec::longToQuotaSize),
                    map.getOrDefault(QuotaType.COUNT, Optional.empty()).flatMap(QuotaCodec::longToQuotaCount));
            })
            .map(limits -> new UserWithSpecificQuota(username, limits));
    }

    private Mono<ExtraQuotaSum> calculateExtraQuota(GroupedFlux<Optional<Domain>, UserWithSpecificQuota> usersQuotaOfADomain) {
        Optional<Domain> maybeDomain = usersQuotaOfADomain.key();
        Mono<QuotaCountLimit> commonCountLimitMono = Mono.justOrEmpty(maybeDomain)
            .flatMap(domain -> getMaxMessageReactive(QuotaScope.DOMAIN, domain.asString()))
            .switchIfEmpty(getMaxMessageReactive(QuotaScope.GLOBAL, GLOBAL_IDENTIFIER));
        Mono<QuotaSizeLimit> commonStorageLimitMono = Mono.justOrEmpty(maybeDomain)
            .flatMap(domain -> getMaxStorageReactive(QuotaScope.DOMAIN, domain.asString()))
            .switchIfEmpty(getMaxStorageReactive(QuotaScope.GLOBAL, GLOBAL_IDENTIFIER));

        return Mono.zip(
                commonStorageLimitMono
                    .map(Optional::of)
                    .switchIfEmpty(Mono.fromCallable(Optional::empty)),
                commonCountLimitMono
                    .map(Optional::of)
                    .switchIfEmpty(Mono.fromCallable(Optional::empty)))
            .map(tuple -> new Limits(tuple.getT1(), tuple.getT2()))
            .flatMap(commonQuota -> usersQuotaOfADomain
                .map(userQuota -> ExtraQuotaSum.calculateExtraQuota(commonQuota, userQuota.limits()))
                .reduce(ExtraQuotaSum.NONE, ExtraQuotaSum::merge));
    }

    private QuotaLimit convertRowToModel(Row row) {
        return QuotaLimit.builder().quotaComponent(QuotaComponent.of(row.get(QUOTA_COMPONENT, String.class)))
            .quotaScope(QuotaScope.of(row.get(QUOTA_SCOPE, String.class)))
            .identifier(row.get(IDENTIFIER, String.class))
            .quotaType(QuotaType.of(row.get(QUOTA_TYPE, String.class)))
            .quotaLimit(row.get(QUOTA_LIMIT, Long.class))
            .build();
    }

    private Predicate<QuotaLimit> isUserScoped() {
        return quotaLimit -> quotaLimit.getQuotaScope().equals(QuotaScope.USER);
    }

    private Predicate<QuotaLimit> isMailboxQuota() {
        return quotaLimit -> quotaLimit.getQuotaComponent().equals(QuotaComponent.MAILBOX);
    }

    private Mono<QuotaCountLimit> getMaxMessageReactive(QuotaScope quotaScope, String identifier) {
        return cassandraQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, quotaScope, identifier, QuotaType.COUNT))
            .map(QuotaLimit::getQuotaLimit)
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaCount)
            .handle(publishIfPresent());
    }

    private Mono<QuotaSizeLimit> getMaxStorageReactive(QuotaScope quotaScope, String identifier) {
        return cassandraQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, quotaScope, identifier, QuotaType.SIZE))
            .map(QuotaLimit::getQuotaLimit)
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaSize)
            .handle(publishIfPresent());
    }
}
