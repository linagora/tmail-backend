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

import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaLimitTable.IDENTIFIER;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaLimitTable.QUOTA_COMPONENT;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaLimitTable.QUOTA_LIMIT;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaLimitTable.QUOTA_SCOPE;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaLimitTable.QUOTA_TYPE;
import static org.apache.james.backends.postgres.quota.PostgresQuotaDataDefinition.PostgresQuotaLimitTable.TABLE_NAME;
import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;
import static org.apache.james.util.ReactorUtils.publishIfPresent;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.backends.postgres.quota.PostgresQuotaLimitDAO;
import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaComponent;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaLimit;
import org.apache.james.core.quota.QuotaScope;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.core.quota.QuotaType;
import org.apache.james.mailbox.quota.QuotaCodec;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.jooq.Record;
import org.reactivestreams.Publisher;

import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.model.ExtraQuotaSum;
import com.linagora.tmail.mailbox.quota.model.Limits;
import com.linagora.tmail.mailbox.quota.model.UserWithSpecificQuota;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class PostgresUserQuotaReporter implements UserQuotaReporter {
    private static final String GLOBAL_IDENTIFIER = "global";

    private final PostgresExecutor postgresExecutor;
    private final PostgresQuotaLimitDAO postgresQuotaLimitDao;
    private final QuotaRootResolver quotaRootResolver;

    @Inject
    public PostgresUserQuotaReporter(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor,
                                     PostgresQuotaLimitDAO postgresQuotaLimitDAO,
                                     QuotaRootResolver quotaRootResolver) {
        this.postgresExecutor = postgresExecutor;
        this.postgresQuotaLimitDao = postgresQuotaLimitDAO;
        this.quotaRootResolver = quotaRootResolver;
    }

    @Override
    public Publisher<UserWithSpecificQuota> usersWithSpecificQuota() {
        return getSpecificUsersQuota();
    }

    @Override
    public Publisher<Long> usersWithSpecificQuotaCount() {
        return getSpecificUsersQuota()
            .count();
    }

    @Override
    public Publisher<ExtraQuotaSum> usersExtraQuotaSum() {
        return getSpecificUsersQuota()
            .groupBy(userQuota -> userQuota.username().getDomainPart())
            .flatMap(this::calculateExtraQuota)
            .reduce(ExtraQuotaSum.NONE, ExtraQuotaSum::merge);
    }

    private Flux<UserWithSpecificQuota> getSpecificUsersQuota() {
        // the query does not use the full composite index key; however, it should be fine as it serves a rare analytic report need
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(QUOTA_COMPONENT.eq(QuotaComponent.MAILBOX.getValue()))
                .and(QUOTA_SCOPE.eq(QuotaScope.USER.getValue()))))
            .map(this::asQuotaLimit)
            .groupBy(QuotaLimit::getIdentifier)
            .flatMap(this::toUserSpecificQuota);
    }

    private QuotaLimit asQuotaLimit(Record record) {
        return QuotaLimit.builder().quotaComponent(QuotaComponent.of(record.get(QUOTA_COMPONENT)))
            .quotaScope(QuotaScope.of(record.get(QUOTA_SCOPE)))
            .identifier(record.get(IDENTIFIER))
            .quotaType(QuotaType.of(record.get(QUOTA_TYPE)))
            .quotaLimit(record.get(QUOTA_LIMIT))
            .build();
    }

    private Mono<UserWithSpecificQuota> toUserSpecificQuota(GroupedFlux<String, QuotaLimit> limitsOfUser) {
        return limitsOfUser
            .collectList()
            .map(quotaLimits -> {
                Map<QuotaType, Optional<Long>> map = quotaLimits.stream()
                    .collect(Collectors.toMap(QuotaLimit::getQuotaType, QuotaLimit::getQuotaLimit));
                return new Limits(
                    map.getOrDefault(QuotaType.SIZE, Optional.empty()).flatMap(QuotaCodec::longToQuotaSize),
                    map.getOrDefault(QuotaType.COUNT, Optional.empty()).flatMap(QuotaCodec::longToQuotaCount));
            })
            .flatMap(limits -> Mono.fromCallable(() -> quotaRootResolver.associatedUsername(quotaRootResolver.fromString(limitsOfUser.key())))
                .map(username -> new UserWithSpecificQuota(username, limits)));
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

    private Mono<QuotaCountLimit> getMaxMessageReactive(QuotaScope quotaScope, String identifier) {
        return postgresQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, quotaScope, identifier, QuotaType.COUNT))
            .map(QuotaLimit::getQuotaLimit)
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaCount)
            .handle(publishIfPresent());
    }

    private Mono<QuotaSizeLimit> getMaxStorageReactive(QuotaScope quotaScope, String identifier) {
        return postgresQuotaLimitDao.getQuotaLimit(QuotaLimit.QuotaLimitKey.of(QuotaComponent.MAILBOX, quotaScope, identifier, QuotaType.SIZE))
            .map(QuotaLimit::getQuotaLimit)
            .handle(publishIfPresent())
            .map(QuotaCodec::longToQuotaSize)
            .handle(publishIfPresent());
    }
}
