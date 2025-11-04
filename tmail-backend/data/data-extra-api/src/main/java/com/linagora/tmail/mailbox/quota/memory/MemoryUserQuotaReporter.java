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

package com.linagora.tmail.mailbox.quota.memory;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.inmemory.quota.InMemoryPerUserMaxQuotaManager;
import org.reactivestreams.Publisher;

import com.linagora.tmail.mailbox.quota.UserQuotaReporter;
import com.linagora.tmail.mailbox.quota.model.ExtraQuotaSum;
import com.linagora.tmail.mailbox.quota.model.Limits;
import com.linagora.tmail.mailbox.quota.model.UserWithSpecificQuota;

import reactor.core.publisher.Flux;
import reactor.core.publisher.GroupedFlux;
import reactor.core.publisher.Mono;

public class MemoryUserQuotaReporter implements UserQuotaReporter {
    private final InMemoryPerUserMaxQuotaManager maxQuotaManager;
    private final Map<String, QuotaSizeLimit> userMaxStorage;
    private final Map<String, QuotaCountLimit> userMaxMessage;

    @Inject
    public MemoryUserQuotaReporter(InMemoryPerUserMaxQuotaManager maxQuotaManager) {
        this.maxQuotaManager = maxQuotaManager;

        try {
            Field userMaxStorageField = InMemoryPerUserMaxQuotaManager.class.getDeclaredField("userMaxStorage");
            Field userMaxMessageField = InMemoryPerUserMaxQuotaManager.class.getDeclaredField("userMaxMessage");
            userMaxStorageField.setAccessible(true);
            userMaxMessageField.setAccessible(true);
            this.userMaxStorage = (Map<String, QuotaSizeLimit>) userMaxStorageField.get(maxQuotaManager);
            this.userMaxMessage = (Map<String, QuotaCountLimit>) userMaxMessageField.get(maxQuotaManager);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access quota maps using reflection", e);
        }
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
        Set<String> usernames = new HashSet<>();
        usernames.addAll(userMaxStorage.keySet());
        usernames.addAll(userMaxMessage.keySet());

        return Flux.fromIterable(usernames)
            .map(username -> new UserWithSpecificQuota(Username.of(username),
                new Limits(
                    Optional.ofNullable(userMaxStorage.get(username)),
                    Optional.ofNullable(userMaxMessage.get(username)))));
    }

    private Mono<ExtraQuotaSum> calculateExtraQuota(GroupedFlux<Optional<Domain>, UserWithSpecificQuota> usersQuotaOfADomain) {
        Optional<Domain> maybeDomain = usersQuotaOfADomain.key();
        Mono<QuotaCountLimit> commonCountLimitMono = Mono.justOrEmpty(maybeDomain)
            .flatMap(this::getDomainMaxMessageReactive)
            .switchIfEmpty(getGlobalMaxMessageReactive());
        Mono<QuotaSizeLimit> commonStorageLimitMono = Mono.justOrEmpty(maybeDomain)
            .flatMap(this::getDomainMaxStorageReactive)
            .switchIfEmpty(getGlobalMaxStorageReactive());

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

    private Mono<QuotaCountLimit> getDomainMaxMessageReactive(Domain domain) {
        return Mono.from(maxQuotaManager.getDomainMaxMessageReactive(domain));
    }

    private Mono<QuotaCountLimit> getGlobalMaxMessageReactive() {
        return Mono.from(maxQuotaManager.getGlobalMaxMessageReactive());
    }

    private Mono<QuotaSizeLimit> getDomainMaxStorageReactive(Domain domain) {
        return Mono.from(maxQuotaManager.getDomainMaxStorageReactive(domain));
    }

    private Mono<QuotaSizeLimit> getGlobalMaxStorageReactive() {
        return Mono.from(maxQuotaManager.getGlobalMaxStorageReactive());
    }
}
