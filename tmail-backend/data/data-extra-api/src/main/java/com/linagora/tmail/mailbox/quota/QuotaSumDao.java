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

package com.linagora.tmail.mailbox.quota;

import org.apache.james.core.Domain;
import org.apache.james.mailbox.model.CurrentQuotas;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.UserQuotaRootResolver;
import org.apache.james.user.api.UsersRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class QuotaSumDao {
    private static final int DEFAULT_CONCURRENCY = 16;

    private final UsersRepository usersRepository;
    private final UserQuotaRootResolver userQuotaRootResolver;
    private final CurrentQuotaManager currentQuotaManager;

    protected QuotaSumDao(UsersRepository usersRepository,
                           UserQuotaRootResolver userQuotaRootResolver,
                           CurrentQuotaManager currentQuotaManager) {
        this.usersRepository = usersRepository;
        this.userQuotaRootResolver = userQuotaRootResolver;
        this.currentQuotaManager = currentQuotaManager;
    }

    public abstract Mono<QuotaSum> globalUsage();

    public Mono<QuotaSum> domainUsage(Domain domain) {
        return Flux.from(usersRepository.listUsersOfADomainReactive(domain))
            .flatMap(user -> Mono.from(currentQuotaManager.getCurrentQuotas(userQuotaRootResolver.forUser(user))), DEFAULT_CONCURRENCY)
            .map(QuotaSumDao::toQuotaSum)
            .reduce(QuotaSum.ZERO, QuotaSum::sum);
    }

    private static QuotaSum toQuotaSum(CurrentQuotas quotas) {
        return new QuotaSum(quotas.count().asLong(), quotas.size().asLong());
    }
}
