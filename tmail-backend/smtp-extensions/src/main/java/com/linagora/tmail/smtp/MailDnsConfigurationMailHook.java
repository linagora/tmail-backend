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

import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.MaybeSender;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.protocols.smtp.hook.HookResult;
import org.apache.james.protocols.smtp.hook.HookReturnCode;
import org.apache.james.protocols.smtp.hook.MailHook;

import com.linagora.tmail.saas.api.DomainSendingValidator;

public class MailDnsConfigurationMailHook implements MailHook {
    private static final HookResult PENDING_ACTIVATION = HookResult.builder()
        .hookReturnCode(HookReturnCode.deny())
        .smtpReturnCode("550")
        .smtpDescription("5.7.1 " + DomainSendingValidator.PENDING_ACTIVATION)
        .build();
    private static final HookResult TEMPORARY_FAILURE = HookResult.builder()
        .hookReturnCode(HookReturnCode.denySoft())
        .smtpReturnCode("451")
        .smtpDescription("4.3.0 Unable to check domain activation")
        .build();

    private final DomainSendingValidator domainSendingValidator;

    @Inject
    public MailDnsConfigurationMailHook(DomainSendingValidator domainSendingValidator) {
        this.domainSendingValidator = domainSendingValidator;
    }

    @Override
    public HookResult doMail(SMTPSession session, MaybeSender sender) {
        // Incoming SMTP delivery must remain available during migration.
        if (session.getUsername() == null && !session.isRelayingAllowed()) {
            return HookResult.DECLINED;
        }
        return domainSendingValidator.canSend(Optional.ofNullable(session.getUsername()), sender)
            .map(allowed -> allowed ? HookResult.DECLINED : PENDING_ACTIVATION)
            .onErrorReturn(TEMPORARY_FAILURE)
            .block();
    }
}
