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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.inject.Inject;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.model.QuotaRoot;

import com.google.common.cache.LoadingCache;
import com.linagora.tmail.mailbox.quota.QuotaSumDao;
import com.linagora.tmail.mailbox.quota.model.QuotaSum;

import reactor.core.publisher.Mono;

public class MemoryQuotaSumDao implements QuotaSumDao {
    private final LoadingCache<QuotaRoot, AtomicReference<CurrentQuotas>> quotaCache;

    @SuppressWarnings("unchecked")
    @Inject
    public MemoryQuotaSumDao(InMemoryCurrentQuotaManager currentQuotaManager) {
        try {
            Field quotaCacheField = InMemoryCurrentQuotaManager.class.getDeclaredField("quotaCache");
            quotaCacheField.setAccessible(true);
            this.quotaCache = (LoadingCache<QuotaRoot, AtomicReference<CurrentQuotas>>) quotaCacheField.get(currentQuotaManager);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access quotaCache using reflection", e);
        }
    }

    @Override
    public Mono<QuotaSum> globalUsage() {
        return Mono.fromCallable(() -> sumMatching(Optional.empty()));
    }

    @Override
    public Mono<QuotaSum> domainUsage(Domain domain) {
        return Mono.fromCallable(() -> sumMatching(Optional.of(domain)));
    }

    private QuotaSum sumMatching(Optional<Domain> domain) {
        QuotaSum sum = QuotaSum.ZERO;
        for (Map.Entry<QuotaRoot, AtomicReference<CurrentQuotas>> entry : quotaCache.asMap().entrySet()) {
            if (domain.isPresent() && !entry.getKey().getDomain().equals(domain)) {
                continue;
            }
            CurrentQuotas quotas = entry.getValue().get();
            long count = Math.max(0L, quotas.count().asLong());
            long size = Math.max(0L, quotas.size().asLong());
            sum = new QuotaSum(sum.count() + count, sum.size() + size);
        }
        return sum;
    }
}
