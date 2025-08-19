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

import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.ALICE;
import static com.linagora.tmail.saas.api.SaaSAccountRepositoryContract.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.apache.mailet.base.test.FakeMatcherConfig;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public class SenderHasSaaSPlanTest {
    @Test
    void shouldReturnRecipientWhenSenderMatchesSaaSPlan() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(ALICE, new SaaSAccount(SaaSPlan.STANDARD))).block();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.PREMIUM))).block();

        SenderHasSaaSPlan senderHasSaaSPlan = new SenderHasSaaSPlan(saaSAccountRepository);
        senderHasSaaSPlan.init(FakeMatcherConfig.builder()
            .matcherName("HasSaaSPlan")
            .condition("standard,premium")
            .build());

        MailAddress recipient = new MailAddress("james-user@james.org");

        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build())).containsOnly(recipient);
        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build()))
            .containsOnly(recipient);
    }

    @Test
    void saasPlansConditionShouldBeCaseInsensitive() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(ALICE, new SaaSAccount(SaaSPlan.STANDARD))).block();

        SenderHasSaaSPlan senderHasSaaSPlan = new SenderHasSaaSPlan(saaSAccountRepository);
        senderHasSaaSPlan.init(FakeMatcherConfig.builder()
            .matcherName("HasSaaSPlan")
            .condition("StanDarD")
            .build());

        MailAddress recipient = new MailAddress("james-user@james.org");

        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(ALICE.asMailAddress())
            .recipient(recipient)
            .build()))
            .containsOnly(recipient);
    }

    @Test
    void shouldNotReturnRecipientWhenSenderDoesNotMatchSaaSPlan() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(ALICE, new SaaSAccount(SaaSPlan.FREE))).block();

        SenderHasSaaSPlan senderHasSaaSPlan = new SenderHasSaaSPlan(saaSAccountRepository);
        senderHasSaaSPlan.init(FakeMatcherConfig.builder()
            .matcherName("HasSaaSPlan")
            .condition("standard,premium")
            .build());

        MailAddress recipient = new MailAddress("james-user@james.org");

        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(ALICE.asMailAddress())
            .recipient(recipient)
            .build()))
            .isEmpty();
    }

    @Test
    void shouldNotReturnRecipientWhenSenderDoesNotHaveASaaSPlan() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();

        SenderHasSaaSPlan senderHasSaaSPlan = new SenderHasSaaSPlan(saaSAccountRepository);
        senderHasSaaSPlan.init(FakeMatcherConfig.builder()
            .matcherName("HasSaaSPlan")
            .condition("standard,premium")
            .build());

        MailAddress recipient = new MailAddress("james-user@james.org");

        assertThat(senderHasSaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(ALICE.asMailAddress())
            .recipient(recipient)
            .build()))
            .isEmpty();
    }
}
