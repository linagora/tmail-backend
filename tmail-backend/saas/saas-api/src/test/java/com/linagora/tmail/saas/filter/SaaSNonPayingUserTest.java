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

package com.linagora.tmail.saas.filter;

import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

class SaaSNonPayingUserTest {
    @Test
    void shouldReturnTrueWhenUserIsNonPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, false))).block();

        SaaSNonPayingUser testee = new SaaSNonPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserIsPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, true))).block();

        SaaSNonPayingUser testee = new SaaSNonPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isFalse();
    }

    @Test
    void shouldReturnTrueWhenNoSaaSAccountStored() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();

        SaaSNonPayingUser testee = new SaaSNonPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isTrue();
    }
}
