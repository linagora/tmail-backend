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

package com.linagora.tmail.smtp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.james.core.Domain;
import org.apache.james.core.MaybeSender;
import org.apache.james.core.Username;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.saas.api.SaaSAccountRepository;
import com.linagora.tmail.saas.api.DomainSendingValidator;
import com.linagora.tmail.saas.api.memory.MemorySaaSAccountRepository;

import reactor.core.publisher.Mono;

class MailDnsConfigurationMailHookTest {
    private static final Domain DOMAIN = Domain.of("migration.example");
    private final MemorySaaSAccountRepository repository = new MemorySaaSAccountRepository();
    private final MailDnsConfigurationMailHook hook = new MailDnsConfigurationMailHook(new DomainSendingValidator(repository));
    private final SMTPSession session = mock(SMTPSession.class);

    @Test
    void authenticatedSubmissionShouldBeRejectedUntilDnsIsValidated() {
        when(session.getUsername()).thenReturn(Username.of("alice@migration.example"));
        Mono.from(repository.setMailDnsConfigurationValidated(DOMAIN, false)).block();
        assertThat(hook.doMail(session, MaybeSender.nullSender()).getSmtpRetCode()).contains("550");
        Mono.from(repository.setMailDnsConfigurationValidated(DOMAIN, true)).block();
        assertThat(hook.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }

    @Test
    void incomingMailShouldNotBeBlocked() {
        Mono.from(repository.setMailDnsConfigurationValidated(DOMAIN, false)).block();
        assertThat(hook.doMail(session, MaybeSender.getMailSender("sender@migration.example")))
            .isEqualTo(HookResult.DECLINED);
    }

    @Test
    void trustedRelayShouldCheckEnvelopeDomain() {
        when(session.isRelayingAllowed()).thenReturn(true);
        Mono.from(repository.setMailDnsConfigurationValidated(DOMAIN, false)).block();
        assertThat(hook.doMail(session, MaybeSender.getMailSender("sender@migration.example")).getSmtpRetCode()).contains("550");
    }

    @Test
    void existingDomainShouldKeepSending() {
        when(session.getUsername()).thenReturn(Username.of("alice@migration.example"));
        assertThat(hook.doMail(session, MaybeSender.nullSender())).isEqualTo(HookResult.DECLINED);
    }

    @Test
    void repositoryFailureShouldTemporarilyRejectSubmission() {
        SaaSAccountRepository failingRepository = mock(SaaSAccountRepository.class);
        when(failingRepository.getMailDnsConfigurationValidated(DOMAIN)).thenReturn(Mono.error(new RuntimeException("Unavailable")));
        when(session.getUsername()).thenReturn(Username.of("alice@migration.example"));
        MailDnsConfigurationMailHook failingHook = new MailDnsConfigurationMailHook(new DomainSendingValidator(failingRepository));
        assertThat(failingHook.doMail(session, MaybeSender.nullSender()).getSmtpRetCode()).contains("451");
    }
}
