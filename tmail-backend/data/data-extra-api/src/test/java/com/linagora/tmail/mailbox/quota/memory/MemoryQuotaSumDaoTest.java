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

import static org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.inmemory.quota.InMemoryCurrentQuotaManager;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.store.quota.CurrentQuotaCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailbox.quota.model.QuotaSum;

import reactor.core.publisher.Mono;

class MemoryQuotaSumDaoTest {
    private static final Domain DOMAIN_1 = Domain.of("domain.tld");
    private static final Domain DOMAIN_2 = Domain.of("domain2.tld");
    private static final QuotaRoot BOB = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + "bob@domain.tld", Optional.of(DOMAIN_1));
    private static final QuotaRoot ANDRE = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + "andre@domain2.tld", Optional.of(DOMAIN_2));
    private static final QuotaRoot LOCAL_ONLY = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + "local", Optional.empty());

    private InMemoryCurrentQuotaManager currentQuotaManager;
    private MemoryQuotaSumDao testee;

    @BeforeEach
    void setUp() {
        currentQuotaManager = new InMemoryCurrentQuotaManager(mock(CurrentQuotaCalculator.class), mock(SessionProvider.class));
        testee = new MemoryQuotaSumDao(currentQuotaManager);
    }

    @Test
    void globalUsageShouldBeZeroByDefault() {
        assertThat(Mono.from(testee.globalUsage()).block()).isEqualTo(QuotaSum.ZERO);
    }

    @Test
    void globalUsageShouldSumCountAndSizeAcrossDomains() {
        Mono.from(currentQuotaManager.setCurrentQuotas(new QuotaOperation(BOB, QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();
        Mono.from(currentQuotaManager.setCurrentQuotas(new QuotaOperation(ANDRE, QuotaCountUsage.count(50L), QuotaSizeUsage.size(500L)))).block();

        assertThat(Mono.from(testee.globalUsage()).block()).isEqualTo(new QuotaSum(60L, 600L));
    }

    @Test
    void domainUsageShouldOnlySumGivenDomain() {
        Mono.from(currentQuotaManager.setCurrentQuotas(new QuotaOperation(BOB, QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();
        Mono.from(currentQuotaManager.setCurrentQuotas(new QuotaOperation(ANDRE, QuotaCountUsage.count(50L), QuotaSizeUsage.size(500L)))).block();

        assertThat(Mono.from(testee.domainUsage(DOMAIN_1)).block()).isEqualTo(new QuotaSum(10L, 100L));
        assertThat(Mono.from(testee.domainUsage(DOMAIN_2)).block()).isEqualTo(new QuotaSum(50L, 500L));
    }

    @Test
    void domainUsageShouldNotMatchUsersWithoutDomain() {
        Mono.from(currentQuotaManager.setCurrentQuotas(new QuotaOperation(LOCAL_ONLY, QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();

        assertThat(Mono.from(testee.domainUsage(DOMAIN_1)).block()).isEqualTo(QuotaSum.ZERO);
    }

    @Test
    void globalUsageShouldAccountForDomainlessUsers() {
        Mono.from(currentQuotaManager.setCurrentQuotas(new QuotaOperation(LOCAL_ONLY, QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();

        assertThat(Mono.from(testee.globalUsage()).block()).isEqualTo(new QuotaSum(10L, 100L));
    }
}
