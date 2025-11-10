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
import org.apache.james.core.quota.QuotaCountLimit;
import org.apache.james.core.quota.QuotaSizeLimit;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.QuotaRoot;
import org.apache.james.mailbox.quota.MaxQuotaManager;
import org.apache.james.mailbox.quota.QuotaRootResolver;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.mailbox.quota.model.ExtraQuotaSum;
import com.linagora.tmail.mailbox.quota.model.Limits;
import com.linagora.tmail.mailbox.quota.model.UserWithSpecificQuota;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UserQuotaReporterContract {

    Domain DOMAIN_1 = Domain.of("domain.tld");
    Domain DOMAIN_2 = Domain.of("domain2.tld");
    Username BOB = Username.of("bob@domain.tld");
    QuotaRoot BOB_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + BOB.asString(), Optional.of(DOMAIN_1));
    Username ALICE = Username.of("alice@domain.tld");
    QuotaRoot ALICE_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + ALICE.asString(), Optional.of(DOMAIN_1));
    Username ANDRE_AT_DOMAIN_2 = Username.of("andre@domain2.tld");
    QuotaRoot ANDRE_AT_DOMAIN_2_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + ANDRE_AT_DOMAIN_2.asString(), Optional.of(DOMAIN_1));
    Username LOCAL_PART_ONLY = Username.of("localpartOnly");
    QuotaRoot LOCAL_PART_ONLY_QUOTA_ROOT = QuotaRoot.quotaRoot(MailboxConstants.USER_NAMESPACE + SEPARATOR + LOCAL_PART_ONLY.asString(), Optional.empty());

    MaxQuotaManager maxQuotaManager();
    UserQuotaReporter testee();

    default QuotaRootResolver quotaRootResolver() throws MailboxException {
        QuotaRootResolver quotaRootResolver = mock(QuotaRootResolver.class);
        when(quotaRootResolver.fromString(BOB_QUOTA_ROOT.getValue()))
            .thenReturn(BOB_QUOTA_ROOT);
        when(quotaRootResolver.fromString(ALICE_QUOTA_ROOT.getValue()))
            .thenReturn(ALICE_QUOTA_ROOT);
        when(quotaRootResolver.fromString(ANDRE_AT_DOMAIN_2_QUOTA_ROOT.getValue()))
            .thenReturn(ANDRE_AT_DOMAIN_2_QUOTA_ROOT);
        when(quotaRootResolver.fromString(LOCAL_PART_ONLY_QUOTA_ROOT.getValue()))
            .thenReturn(LOCAL_PART_ONLY_QUOTA_ROOT);
        when(quotaRootResolver.associatedUsername(BOB_QUOTA_ROOT))
            .thenReturn(BOB);
        when(quotaRootResolver.associatedUsername(ALICE_QUOTA_ROOT))
            .thenReturn(ALICE);
        when(quotaRootResolver.associatedUsername(ANDRE_AT_DOMAIN_2_QUOTA_ROOT))
            .thenReturn(ANDRE_AT_DOMAIN_2);
        when(quotaRootResolver.associatedUsername(LOCAL_PART_ONLY_QUOTA_ROOT))
            .thenReturn(LOCAL_PART_ONLY);
        return quotaRootResolver;
    }

    @Test
    default void shouldCountZeroUsersSpecificQuotaByDefault() {
        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isZero();
    }

    @Test
    default void shouldCountZeroUsersSpecificQuotaWhenOnlyDomainLimit() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(1000));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isZero();
    }

    @Test
    default void shouldCountZeroUsersSpecificQuotaWhenOnlyGlobalLimit() throws MailboxException {
        maxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(1000));
        maxQuotaManager().setGlobalMaxMessage(QuotaCountLimit.count(100));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isZero();
    }

    @Test
    default void shouldCountUsersWithSpecificStorageQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.size(10001));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isEqualTo(2L);
    }

    @Test
    default void shouldCountUsersWithSpecificCountQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(10000));
        maxQuotaManager().setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(10001));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isEqualTo(2L);
    }

    @Test
    default void shouldCountUsersWithMixedSpecificQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(10001));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isEqualTo(2L);
    }

    @Test
    default void shouldCountUsersWithUnlimitedQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.unlimited());

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isEqualTo(1L);
    }

    @Test
    default void shouldCountUsersWithSpecificQuotaAcrossDomains() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxStorage(DOMAIN_2, QuotaSizeLimit.size(200));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxStorage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaSizeLimit.size(20000));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isEqualTo(2L);
    }

    @Test
    default void shouldCountUsersWithSpecificQuotaWhenVirtualHostingDisabled() throws MailboxException {
        maxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        maxQuotaManager().setMaxStorage(LOCAL_PART_ONLY_QUOTA_ROOT, QuotaSizeLimit.size(10000));

        assertThat(Mono.from(testee().usersWithSpecificQuotaCount()).block())
            .isEqualTo(1L);
    }

    @Test
    default void shouldReturnEmptyUsersSpecificQuotaByDefault() {
        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .isEmpty();
    }

    @Test
    default void shouldReturnEmptyUsersSpecificQuotaWhenOnlyDomainLimit() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(1000));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .isEmpty();
    }

    @Test
    default void shouldReturnEmptyUsersSpecificQuotaWhenOnlyGlobalLimit() throws MailboxException {
        maxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(1000));
        maxQuotaManager().setGlobalMaxMessage(QuotaCountLimit.count(100));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .isEmpty();
    }

    @Test
    default void shouldReturnUsersWithSpecificStorageQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.size(10001));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .containsOnly(
                new UserWithSpecificQuota(BOB, new Limits(Optional.of(QuotaSizeLimit.size(10000)), Optional.empty())),
                new UserWithSpecificQuota(ALICE, new Limits(Optional.of(QuotaSizeLimit.size(10001)), Optional.empty())));
    }

    @Test
    default void shouldReturnUsersWithSpecificCountQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(10000));
        maxQuotaManager().setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(10001));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .containsOnly(
                new UserWithSpecificQuota(BOB, new Limits(Optional.empty(), Optional.of(QuotaCountLimit.count(10000)))),
                new UserWithSpecificQuota(ALICE, new Limits(Optional.empty(), Optional.of(QuotaCountLimit.count(10001)))));
    }

    @Test
    default void shouldReturnUsersWithMixedSpecificQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(10001));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .containsOnly(
                new UserWithSpecificQuota(BOB, new Limits(Optional.of(QuotaSizeLimit.size(10000)), Optional.of(QuotaCountLimit.count(10001)))));
    }

    @Test
    default void shouldReturnUsersWithUnlimitedQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.unlimited());

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .containsOnly(
                new UserWithSpecificQuota(BOB, new Limits(Optional.of(QuotaSizeLimit.unlimited()), Optional.of(QuotaCountLimit.unlimited()))));
    }

    @Test
    default void shouldReturnUsersWithSpecificQuotaAcrossDomains() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxStorage(DOMAIN_2, QuotaSizeLimit.size(200));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxStorage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaSizeLimit.size(20000));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .containsOnly(
                new UserWithSpecificQuota(BOB, new Limits(Optional.of(QuotaSizeLimit.size(10000)), Optional.empty())),
                new UserWithSpecificQuota(ANDRE_AT_DOMAIN_2, new Limits(Optional.of(QuotaSizeLimit.size(20000)), Optional.empty())));
    }

    @Test
    default void shouldReturnUsersWithSpecificQuotaWhenVirtualHostingDisabled() throws MailboxException {
        maxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));

        maxQuotaManager().setMaxStorage(LOCAL_PART_ONLY_QUOTA_ROOT, QuotaSizeLimit.size(10000));
        maxQuotaManager().setMaxMessage(LOCAL_PART_ONLY_QUOTA_ROOT, QuotaCountLimit.count(10001));

        assertThat(Flux.from(testee().usersWithSpecificQuota()).collectList().block())
            .containsOnly(
                new UserWithSpecificQuota(LOCAL_PART_ONLY, new Limits(Optional.of(QuotaSizeLimit.size(10000)), Optional.of(QuotaCountLimit.count(10001)))));
    }

    @Test
    default void shouldReturnZeroExtraQuotaByDefault() {
        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(ExtraQuotaSum.NONE);
    }

    @Test
    default void shouldReturnZeroExtraQuotaByDefaultWhenOnlyDomainQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(1000));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(ExtraQuotaSum.NONE);
    }

    @Test
    default void shouldReturnZeroExtraQuotaByDefaultWhenOnlyGlobalQuota() throws MailboxException {
        maxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(1000));
        maxQuotaManager().setGlobalMaxMessage(QuotaCountLimit.count(100));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(ExtraQuotaSum.NONE);
    }

    @Test
    default void shouldReturnExtraQuotaWhenDomainQuotaIsSet() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(10));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(150));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(15));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(50), QuotaCountLimit.count(5)));
    }

    @Test
    default void shouldReturnExtraQuotaWhenOnlyGlobalQuotaIsSet() throws MailboxException {
        // no domain quota is set, then should use global quota to calculate the extra quota
        maxQuotaManager().setGlobalMaxStorage(QuotaSizeLimit.size(100));
        maxQuotaManager().setGlobalMaxMessage(QuotaCountLimit.count(10));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(150));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(15));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(50), QuotaCountLimit.count(5)));
    }

    @Test
    default void shouldReturnExtraQuotaMultipleUsersCase() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(100));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(100));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(200));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(200));

        maxQuotaManager().setMaxStorage(ALICE_QUOTA_ROOT, QuotaSizeLimit.size(300));
        maxQuotaManager().setMaxMessage(ALICE_QUOTA_ROOT, QuotaCountLimit.count(300));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(300), QuotaCountLimit.count(300)));
    }

    @Test
    default void shouldReturnExtraQuotaAcrossDomains() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(101));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(101));
        maxQuotaManager().setDomainMaxStorage(DOMAIN_2, QuotaSizeLimit.size(201));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_2, QuotaCountLimit.count(201));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(201));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(201));
        maxQuotaManager().setMaxStorage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaSizeLimit.size(301));
        maxQuotaManager().setMaxMessage(ANDRE_AT_DOMAIN_2_QUOTA_ROOT, QuotaCountLimit.count(301));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(200), QuotaCountLimit.count(200)));
    }

    @Test
    default void shouldReturnZeroExtraQuotaWhenBothDomainQuotaAndUserQuotaAreUnlimited() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.unlimited());
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.unlimited());

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.unlimited());

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(0L), QuotaCountLimit.count(0L)));
    }

    @Test
    default void shouldReturnZeroExtraQuotaWhenDomainQuotaIsUnlimitedAndUserQuotaIsLimited() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.unlimited());
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.unlimited());

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(200));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(20));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(0L), QuotaCountLimit.count(0L)));
    }

    @Test
    default void shouldReturnUnlimitedExtraQuotaWhenDomainQuotaIsLimitedAndUserQuotaIsUnlimited() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(200));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(20));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.unlimited());
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.unlimited());

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.unlimited(), QuotaCountLimit.unlimited()));
    }

    @Test
    default void shouldReturnZeroExtraQuotaWhenUserQuotaIsLessThanDomainQuota() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(200));
        maxQuotaManager().setDomainMaxMessage(DOMAIN_1, QuotaCountLimit.count(20));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(100));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(10));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(0), QuotaCountLimit.count(0)));
    }

    @Test
    default void shouldReturnZeroExtraQuotaWhenNeitherDomainQuotaOrGlobalQuotaIsSet() throws MailboxException {
        // no domain or global quota is set, which mean there are no limits

        // Admin limits Bob quota
        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(150));
        maxQuotaManager().setMaxMessage(BOB_QUOTA_ROOT, QuotaCountLimit.count(15));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block())
            .isEqualTo(new ExtraQuotaSum(QuotaSizeLimit.size(0), QuotaCountLimit.count(0)));
    }

    @Test
    default void shouldReturnZeroExtraCountWhenCountQuotaIsNotSet() throws MailboxException {
        maxQuotaManager().setDomainMaxStorage(DOMAIN_1, QuotaSizeLimit.size(200));

        maxQuotaManager().setMaxStorage(BOB_QUOTA_ROOT, QuotaSizeLimit.size(300));

        assertThat(Mono.from(testee().usersExtraQuotaSum()).block().countLimit())
            .isEqualTo(QuotaCountLimit.count(0));
    }

}
