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

package com.linagora.tmail.saas.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public interface SaaSAccountRepositoryContract {
    SaaSAccountRepository testee();

    Username BOB = Username.of("bob@domain.tld");
    Username ALICE = Username.of("alice@domain.tld");

    @Test
    default void upsertSaasAccountShouldSucceed() {
        Mono.from(testee().upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.FREE))).block();

        assertThat(Mono.from(testee().getSaaSAccount(BOB)).block())
            .isEqualTo(new SaaSAccount(SaaSPlan.FREE));
    }

    @Test
    default void upsertSaasAccountShouldOverridePreviousPlan() {
        Mono.from(testee().upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.FREE))).block();
        Mono.from(testee().upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.PREMIUM))).block();

        assertThat(Mono.from(testee().getSaaSAccount(BOB)).block().saaSPlan())
            .isEqualTo(SaaSPlan.PREMIUM);
    }

    @Test
    default void getSaaSAccountShouldReturnFreePlanByDefault() {
        assertThat(Mono.from(testee().getSaaSAccount(ALICE)).block().saaSPlan())
            .isEqualTo(SaaSPlan.FREE);
    }
}
