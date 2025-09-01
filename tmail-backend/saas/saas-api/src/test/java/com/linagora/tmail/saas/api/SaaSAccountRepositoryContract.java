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

import reactor.core.publisher.Mono;

public interface SaaSAccountRepositoryContract {

    SaaSAccount SAAS_ACCOUNT = new SaaSAccount(false, true);
    SaaSAccount SAAS_ACCOUNT_2 = new SaaSAccount(true, true);

    SaaSAccountRepository testee();

    Username BOB = Username.of("bob@domain.tld");
    Username ALICE = Username.of("alice@domain.tld");

    @Test
    default void upsertSaasAccountShouldSucceed() {
        Mono.from(testee().upsertSaasAccount(BOB, SAAS_ACCOUNT)).block();

        assertThat(Mono.from(testee().getSaaSAccount(BOB)).block())
            .isEqualTo(SAAS_ACCOUNT);
    }

    @Test
    default void getSaaSAccountShouldReturnDefaultValue() {
        assertThat(Mono.from(testee().getSaaSAccount(BOB)).block())
            .isEqualTo(SaaSAccount.DEFAULT);
    }

    @Test
    default void upsertSaasAccountShouldOverridePreviousPlan() {
        Mono.from(testee().upsertSaasAccount(BOB, SAAS_ACCOUNT)).block();
        Mono.from(testee().upsertSaasAccount(BOB, SAAS_ACCOUNT_2)).block();

        assertThat(Mono.from(testee().getSaaSAccount(BOB)).block())
            .isEqualTo(SAAS_ACCOUNT_2);
    }
}
