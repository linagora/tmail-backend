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
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;

import org.apache.james.core.MailAddress;
import org.apache.mailet.base.test.FakeMail;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;
import com.linagora.tmail.saas.model.SaaSAccount;
import com.linagora.tmail.saas.model.SaaSPlan;

import reactor.core.publisher.Mono;

public class SenderHasAnySaaSPlanTest {
    @Test
    void shouldReturnRecipientWhenSenderIsPremiumUser() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.PREMIUM))).block();

        SenderHasAnySaaSPlan senderHasAnySaaSPlan = new SenderHasAnySaaSPlan(saaSAccountRepository);

        MailAddress recipient = new MailAddress("james-user@james.org");
        Collection<MailAddress> matched = senderHasAnySaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build());

        assertThat(matched).containsOnly(recipient);
    }

    @Test
    void shouldReturnRecipientWhenSenderIsStandardUser() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.STANDARD))).block();

        SenderHasAnySaaSPlan senderHasAnySaaSPlan = new SenderHasAnySaaSPlan(saaSAccountRepository);

        MailAddress recipient = new MailAddress("james-user@james.org");
        Collection<MailAddress> matched = senderHasAnySaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build());

        assertThat(matched).containsOnly(recipient);
    }

    @Test
    void shouldReturnRecipientWhenSenderIsFreeUser() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        Mono.from(saaSAccountRepository.upsertSaasAccount(BOB, new SaaSAccount(SaaSPlan.FREE))).block();

        SenderHasAnySaaSPlan senderHasAnySaaSPlan = new SenderHasAnySaaSPlan(saaSAccountRepository);

        MailAddress recipient = new MailAddress("james-user@james.org");
        Collection<MailAddress> matched = senderHasAnySaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build());

        assertThat(matched).containsOnly(recipient);
    }

    @Test
    void shouldNotReturnRecipientWhenSenderDoesNotHaveAnyPlan() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        SenderHasAnySaaSPlan senderHasAnySaaSPlan = new SenderHasAnySaaSPlan(saaSAccountRepository);

        MailAddress recipient = new MailAddress("james-user@james.org");
        Collection<MailAddress> matched = senderHasAnySaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender(BOB.asMailAddress())
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }

    @Test
    void shouldNotReturnRecipientWhenExternalSender() throws Exception {
        SaaSAccountRepository saaSAccountRepository = new MemorySaaSAccountRepository();
        SenderHasAnySaaSPlan senderHasAnySaaSPlan = new SenderHasAnySaaSPlan(saaSAccountRepository);

        MailAddress recipient = new MailAddress("james-user@james.org");
        Collection<MailAddress> matched = senderHasAnySaaSPlan.match(FakeMail.builder()
            .name("default-id")
            .sender("externalSender@gmail.com")
            .recipient(recipient)
            .build());

        assertThat(matched).isEmpty();
    }
}
