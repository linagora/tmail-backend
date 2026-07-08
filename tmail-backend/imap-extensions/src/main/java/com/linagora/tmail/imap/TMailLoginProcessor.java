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
 ********************************************************************/

package com.linagora.tmail.imap;

import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.LoginProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;

import com.google.inject.Inject;
import com.linagora.tmail.team.TeamMailbox;

/**
 * Adds team mailbox impersonation on top of James' {@link LoginProcessor}.
 *
 * The {@code +} delegation parsing lives in {@link com.linagora.tmail.sasl.TMailPlainSaslMechanism};
 * this processor (1) combines a team mailbox {@link org.apache.james.mailbox.Authorizator} into the
 * SASL flow so a member/manager is allowed to act as a team mailbox, and (2) turns such a successful
 * authorization into a non-delegated {@link org.apache.james.mailbox.MailboxSession} scoped to the
 * team mailbox rather than a (necessarily failing) user delegation onto a non-existent team mailbox
 * user. The scope is applied before the INBOX is provisioned.
 */
public class TMailLoginProcessor extends LoginProcessor {
    private final TeamMailboxImpersonationResolver teamMailboxImpersonationResolver;

    @Inject
    public TMailLoginProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                               MetricFactory metricFactory, PathConverter.Factory pathConverterFactory,
                               JamesSaslAuthenticator jamesSaslAuthenticator,
                               TeamMailboxImpersonationResolver teamMailboxImpersonationResolver) {
        super(mailboxManager, factory, metricFactory, pathConverterFactory,
            jamesSaslAuthenticator.withExtraAuthorizator(teamMailboxImpersonationResolver.asAuthorizator()));
        this.teamMailboxImpersonationResolver = teamMailboxImpersonationResolver;
    }

    @Override
    protected void handleSaslSuccess(SaslStep.Success success, ImapSession session, ImapRequest request, Responder responder, String successLog) {
        SaslIdentity identity = success.identity();
        Optional<TeamMailbox> teamMailboxScope = teamMailboxImpersonationResolver.resolveScope(identity);
        if (teamMailboxScope.isPresent()) {
            Username member = identity.authenticationId();
            doAuth(() -> TeamMailboxScope.scopedSession(getMailboxManager(), member, teamMailboxScope.get()),
                session, request, responder, member, identity.authorizationId(), successLog);
            return;
        }
        super.handleSaslSuccess(success, session, request, responder, successLog);
    }
}
