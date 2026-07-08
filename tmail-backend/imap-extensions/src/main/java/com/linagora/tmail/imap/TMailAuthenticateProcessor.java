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

import java.util.List;
import java.util.Optional;

import org.apache.james.imap.api.ImapConfiguration;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.AuthenticateProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.sasl.JamesSaslAuthenticator;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;

/**
 * Adds team mailbox impersonation on top of James' {@link AuthenticateProcessor}.
 *
 * The {@code +} delegation parsing lives in {@link com.linagora.tmail.sasl.TMailPlainSaslMechanism};
 * this processor (1) combines a team mailbox {@link org.apache.james.mailbox.Authorizator} into the
 * SASL flow so a member/manager (or a server administrator) is allowed to act as a team mailbox, and (2) turns such a successful
 * authorization into a non-delegated {@link org.apache.james.mailbox.MailboxSession} scoped to the
 * team mailbox rather than a (necessarily failing) user delegation onto a non-existent team mailbox
 * user. The scope is applied before the INBOX is provisioned.
 */
public class TMailAuthenticateProcessor extends AuthenticateProcessor {
    private final TeamMailboxImpersonationResolver teamMailboxImpersonationResolver;
    private List<String> imapAdminUsers;

    @Inject
    public TMailAuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                      MetricFactory metricFactory, PathConverter.Factory pathConverterFactory,
                                      JamesSaslAuthenticator jamesSaslAuthenticator,
                                      TeamMailboxImpersonationResolver teamMailboxImpersonationResolver) {
        super(mailboxManager, factory, metricFactory, pathConverterFactory,
            jamesSaslAuthenticator.withExtraAuthorizator(teamMailboxImpersonationResolver.asAuthorizator()));
        this.teamMailboxImpersonationResolver = teamMailboxImpersonationResolver;
        this.imapAdminUsers = ImmutableList.of();
    }

    @Override
    public void configure(ImapConfiguration imapConfiguration) {
        super.configure(imapConfiguration);
        this.imapAdminUsers = imapConfiguration.getAdminUsers();
    }

    @Override
    protected void handleSaslSuccess(SaslStep.Success success, ImapSession session, ImapRequest request, Responder responder, String successLog) {
        SaslIdentity identity = success.identity();
        Optional<TeamMailboxImpersonation> teamMailboxScope = teamMailboxImpersonationResolver.resolveScope(identity, imapAdminUsers);
        if (teamMailboxScope.isPresent()) {
            TeamMailboxImpersonation impersonation = teamMailboxScope.get();
            doAuth(() -> TeamMailboxScope.scopedSession(getMailboxManager(), impersonation.sessionUser(), impersonation.teamMailbox()),
                session, request, responder, identity.authenticationId(), identity.authorizationId(), successLog);
        } else {
            super.handleSaslSuccess(success, session, request, responder, successLog);
        }
    }
}
