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

import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.SAAS_ACCOUNT;
import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.SAAS_ACCOUNT_2;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

public class SaaSAccountUsernameChangeTaskStepTest {
    private static final Username OLD = Username.of("alice");
    private static final Username NEW = Username.of("bob");

    private SaaSAccountRepository saasAccountRepository;
    private SaaSAccountUsernameChangeTaskStep testee;

    @BeforeEach
    void setUp() {
        saasAccountRepository = new MemorySaaSAccountRepository();
        testee = new SaaSAccountUsernameChangeTaskStep(saasAccountRepository);
    }

    @Test
    void shouldMigrateSaaSAccountWhenNewUserHasNoSaaSAccount() {
        Mono.from(saasAccountRepository.upsertSaasAccount(OLD, SAAS_ACCOUNT)).block();
        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(saasAccountRepository.getSaaSAccount(NEW)).block())
            .isEqualTo(SAAS_ACCOUNT);
    }

    @Test
    void shouldRevokeOldUserSaaSAccount() {
        Mono.from(saasAccountRepository.upsertSaasAccount(OLD, SAAS_ACCOUNT)).block();
        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(saasAccountRepository.getSaaSAccount(OLD)).block())
            .isEqualTo(SaaSAccount.DEFAULT);
    }

    @Test
    void shouldNotOverrideNewUserExistingSaaSAccount() {
        Mono.from(saasAccountRepository.upsertSaasAccount(OLD, SAAS_ACCOUNT)).block();
        Mono.from(saasAccountRepository.upsertSaasAccount(NEW, SAAS_ACCOUNT_2)).block();

        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(saasAccountRepository.getSaaSAccount(NEW)).block())
            .isEqualTo(SAAS_ACCOUNT_2);
    }

    @Test
    void shouldSucceedWhenOldUserHasNoSaaSAccount() {
        Mono.from(testee.changeUsername(OLD, NEW)).block();

        assertThat(Mono.from(saasAccountRepository.getSaaSAccount(NEW)).block())
            .isEqualTo(SaaSAccount.DEFAULT);
    }
}
