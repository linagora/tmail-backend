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

package com.linagora.tmail.saas.matcher;

import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.BOB;
import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.RATE_LIMITED;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;

import reactor.core.publisher.Mono;

class IsPayingTest {
    @Test
    void shouldReturnRecipientWhenSenderIsPaying() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, true, RATE_LIMITED))).block();

        IsPaying senderHasSaaSPlan = new IsPaying(saaSAccountRepository);
        senderHasSaaSPlan.init(FakeMatcherConfig.builder()
            .matcherName("IsPaying")
            .build());

        MailAddress recipient = new MailAddress("james-user@james.org");

        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build()))
            .containsOnly(recipient);
    }
    @Test
    void shouldReturnEmptyWhenSenderIsNotPaying() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(true, false, RATE_LIMITED))).block();

        IsPaying senderHasSaaSPlan = new IsPaying(saaSAccountRepository);
        senderHasSaaSPlan.init(FakeMatcherConfig.builder()
            .matcherName("IsPaying")
            .build());

        MailAddress recipient = new MailAddress("james-user@james.org");

        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build()))
            .isEmpty();
    }
}
