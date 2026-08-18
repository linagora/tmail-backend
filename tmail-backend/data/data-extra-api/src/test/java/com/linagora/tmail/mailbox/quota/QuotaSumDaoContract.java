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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.core.quota.QuotaCountUsage;
import org.apache.james.core.quota.QuotaSizeUsage;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.QuotaOperation;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.CurrentQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;

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
    Username LOCAL_PART_ONLY = Username.of("localpartOnly");
    QuotaRoot LOCAL_PART_ONLY_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + LOCAL_PART_ONLY.asString(), Optional.empty());

    QuotaSumDao testee();
    CurrentQuotaManager currentQuotaManager();

    default QuotaRootResolver quotaRootResolver() throws MailboxException {
        QuotaRootResolver quotaRootResolver = mock(QuotaRootResolver.class);
        when(quotaRootResolver.fromString(BOB_QUOTA_ROOT.asString()))
            .thenReturn(BOB_QUOTA_ROOT);
        when(quotaRootResolver.fromString(ALICE_QUOTA_ROOT.asString()))
            .thenReturn(ALICE_QUOTA_ROOT);
        when(quotaRootResolver.fromString(ANDRE_QUOTA_ROOT.asString()))
            .thenReturn(ANDRE_QUOTA_ROOT);
        when(quotaRootResolver.fromString(LOCAL_PART_ONLY_QUOTA_ROOT.asString()))
            .thenReturn(LOCAL_PART_ONLY_QUOTA_ROOT);
        when(quotaRootResolver.associatedUsername(BOB_QUOTA_ROOT))
            .thenReturn(BOB);
        when(quotaRootResolver.associatedUsername(ALICE_QUOTA_ROOT))
            .thenReturn(ALICE);
        when(quotaRootResolver.associatedUsername(ANDRE_QUOTA_ROOT))
            .thenReturn(ANDRE);
        when(quotaRootResolver.associatedUsername(LOCAL_PART_ONLY_QUOTA_ROOT))
            .thenReturn(LOCAL_PART_ONLY);
        return quotaRootResolver;
    }

    default void setCurrentQuotas(QuotaRoot quotaRoot, long count, long size) {
        Mono.from(currentQuotaManager().setCurrentQuotas(
            new QuotaOperation(quotaRoot, QuotaCountUsage.count(count), QuotaSizeUsage.size(size)))).block();
    }

    @org.junit.jupiter.api.Test
    default void globalUsageShouldBeZeroByDefault() {
        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(QuotaSum.ZERO);
    }

    @org.junit.jupiter.api.Test
    default void globalUsageShouldSumCountAndSizeAcrossUsersAndDomains() {
        setCurrentQuotas(BOB_QUOTA_ROOT, 5, 500);
        setCurrentQuotas(ALICE_QUOTA_ROOT, 3, 300);
        setCurrentQuotas(ANDRE_QUOTA_ROOT, 2, 200);

        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(new QuotaSum(10, 1000));
    }

    @org.junit.jupiter.api.Test
    default void globalUsageShouldOnlyAccountPositiveValues() {
        setCurrentQuotas(BOB_QUOTA_ROOT, -5, -100);
        setCurrentQuotas(ALICE_QUOTA_ROOT, 3, 300);

        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(new QuotaSum(3, 300));
    }

    @org.junit.jupiter.api.Test
    default void domainUsageShouldReturnZeroWhenNoUsageForDomain() {
        setCurrentQuotas(BOB_QUOTA_ROOT, 5, 500);

        assertThat(Mono.from(testee().domainUsage(DOMAIN_2)).block())
            .isEqualTo(QuotaSum.ZERO);
    }

    @org.junit.jupiter.api.Test
    default void domainUsageShouldSumOnlyMatchingDomain() {
        setCurrentQuotas(BOB_QUOTA_ROOT, 5, 500);
        setCurrentQuotas(ALICE_QUOTA_ROOT, 3, 300);
        setCurrentQuotas(ANDRE_QUOTA_ROOT, 2, 200);

        assertThat(Mono.from(testee().domainUsage(DOMAIN_1)).block())
            .isEqualTo(new QuotaSum(8, 800));
        assertThat(Mono.from(testee().domainUsage(DOMAIN_2)).block())
            .isEqualTo(new QuotaSum(2, 200));
    }

    @org.junit.jupiter.api.Test
    default void domainUsageShouldOnlyAccountPositiveValues() {
        setCurrentQuotas(BOB_QUOTA_ROOT, -5, 500);
        setCurrentQuotas(ALICE_QUOTA_ROOT, 3, -300);

        assertThat(Mono.from(testee().domainUsage(DOMAIN_1)).block())
            .isEqualTo(new QuotaSum(3, 500));
    }

    @org.junit.jupiter.api.Test
    default void domainUsageShouldNotAccountUsersWithoutDomain() {
        setCurrentQuotas(LOCAL_PART_ONLY_QUOTA_ROOT, 7, 700);

        assertThat(Mono.from(testee().domainUsage(DOMAIN_1)).block())
            .isEqualTo(QuotaSum.ZERO);
        assertThat(Mono.from(testee().globalUsage()).block())
            .isEqualTo(new QuotaSum(7, 700));
    }
}
