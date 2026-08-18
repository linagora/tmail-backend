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

import static org.apache.james.mailbox.store.quota.DefaultUserQuotaRootResolver.SEPARATOR;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailbox.quota.model.QuotaSum;

import reactor.core.publisher.Mono;

public interface QuotaSumDaoContract {
    Domain DOMAIN_1 = Domain.of("domain.tld");
    Domain DOMAIN_2 = Domain.of("domain2.tld");
    Username BOB = Username.of("bob@domain.tld");
    QuotaRoot BOB_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + BOB.asString(), Optional.of(DOMAIN_1));
    Username ALICE = Username.of("alice@domain.tld");
    QuotaRoot ALICE_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + ALICE.asString(), Optional.of(DOMAIN_1));
    Username ANDRE = Username.of("andre@domain2.tld");
    QuotaRoot ANDRE_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + ANDRE.asString(), Optional.of(DOMAIN_2));
    Username LOCAL_ONLY = Username.of("localpartOnly");
    QuotaRoot LOCAL_ONLY_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + LOCAL_ONLY.asString(), Optional.empty());

    CurrentQuotaManager currentQuotaManager();
    QuotaSumDao testee();

    @Test
    default void globalUsageShouldBeZeroByDefault() {
        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(QuotaSum.ZERO);
    }

    @Test
    default void domainUsageShouldBeZeroByDefault() {
        assertThat(Mono.from(testee().domainUsage(DOMAIN_1)).block())
            .isEqualTo(QuotaSum.ZERO);
    }

    @Test
    default void globalUsageShouldSumCountAndSize() {
        Mono.from(currentQuotaManager().increase(new QuotaOperation(BOB_QUOTA_ROOT,
            QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();
        Mono.from(currentQuotaManager().increase(new QuotaOperation(ALICE_QUOTA_ROOT,
            QuotaCountUsage.count(20L), QuotaSizeUsage.size(200L)))).block();

        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(new QuotaSum(30L, 300L));
    }

    @Test
    default void domainUsageShouldOnlySumGivenDomain() {
        Mono.from(currentQuotaManager().increase(new QuotaOperation(BOB_QUOTA_ROOT,
            QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();
        Mono.from(currentQuotaManager().increase(new QuotaOperation(ANDRE_QUOTA_ROOT,
            QuotaCountUsage.count(50L), QuotaSizeUsage.size(500L)))).block();

        assertThat(Mono.from(testee().domainUsage(DOMAIN_1)).block())
            .isEqualTo(new QuotaSum(10L, 100L));
        assertThat(Mono.from(testee().domainUsage(DOMAIN_2)).block())
            .isEqualTo(new QuotaSum(50L, 500L));
    }

    @Test
    default void globalUsageShouldAggregateAcrossDomains() {
        Mono.from(currentQuotaManager().increase(new QuotaOperation(BOB_QUOTA_ROOT,
            QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();
        Mono.from(currentQuotaManager().increase(new QuotaOperation(ANDRE_QUOTA_ROOT,
            QuotaCountUsage.count(50L), QuotaSizeUsage.size(500L)))).block();

        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(new QuotaSum(60L, 600L));
    }

    @Test
    default void domainUsageShouldNotMatchUsersWithoutDomain() {
        Mono.from(currentQuotaManager().increase(new QuotaOperation(LOCAL_ONLY_QUOTA_ROOT,
            QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();

        assertThat(Mono.from(testee().domainUsage(DOMAIN_1)).block())
            .isEqualTo(QuotaSum.ZERO);
    }

    @Test
    default void domainUsageShouldBeAccountedForInGlobalUsage() {
        Mono.from(currentQuotaManager().increase(new QuotaOperation(LOCAL_ONLY_QUOTA_ROOT,
            QuotaCountUsage.count(10L), QuotaSizeUsage.size(100L)))).block();

        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(new QuotaSum(10L, 100L));
    }
}
