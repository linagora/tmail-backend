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

public class SaaSPayingUserTest {
    @Test
    void shouldReturnTrueWhenUserIsPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, true))).block();

        SaaSPayingUser testee = new SaaSPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isTrue();
    }

    @Test
    void shouldReturnFalseWhenUserIsNotPaying() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, false))).block();

        SaaSPayingUser testee = new SaaSPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isFalse();
    }

    @Test
    void shouldReturnFalseWhenNoSaaSAccountStored() {
        MemorySaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();

        SaaSPayingUser testee = new SaaSPayingUser(saaSAccountRepository);

        assertThat(testee.isEligible(BOB).block()).isFalse();
    }
}
