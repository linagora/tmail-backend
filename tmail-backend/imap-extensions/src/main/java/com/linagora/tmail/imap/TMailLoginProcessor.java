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
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.message.request.LoginRequest;
import org.apache.james.imap.processor.LoginProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

public class TMailLoginProcessor extends LoginProcessor {
    private static final String DELEGATION_SPLIT_CHARACTER = "+";
    private static final Logger LOGGER = LoggerFactory.getLogger(TMailLoginProcessor.class);

    @Inject
    public TMailLoginProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                               MetricFactory metricFactory, PathConverter.Factory pathConverterFactory) {
        super(mailboxManager, factory, metricFactory, pathConverterFactory);
    }

    @Override
    protected void processRequest(LoginRequest request, ImapSession session, Responder responder) {
        if (session.isPlainAuthDisallowed()) {
            LOGGER.warn("Login rejected because it is disabled or not allowed over insecure channel");
            no(request, responder, HumanReadableText.DISABLED_LOGIN);
            return;
        }

        Username requestUserid = request.getUserid();
        String requestPassword = request.getPassword();
        String localPart = requestUserid.getLocalPart();
        if (!localPart.contains(DELEGATION_SPLIT_CHARACTER)) {
            doPasswordAuth(noDelegation(requestUserid, requestPassword), session, request, responder);
        } else {
            String authenticationId = StringUtils.substringBefore(localPart, DELEGATION_SPLIT_CHARACTER);
            String authorizationId = StringUtils.substringAfter(localPart, DELEGATION_SPLIT_CHARACTER);

            if (StringUtils.isAnyEmpty(authenticationId, authorizationId)) {
                authFailure(session, request, responder, HumanReadableText.INVALID_CREDENTIALS,
                    Optional.of(authenticationId).filter(Predicate.not(String::isEmpty)).map(Username::of),
                    Optional.of(authorizationId).filter(Predicate.not(String::isEmpty)).map(Username::of),
                    "Malformed authentication command."
                 );
            } else {
                Username authenticationUser = Username.of(authenticationId).withDefaultDomain(requestUserid.getDomainPart());
                Username authorizationUser = Username.of(authorizationId).withDefaultDomain(requestUserid.getDomainPart());

                AuthenticationAttempt authenticationAttempt = new AuthenticationAttempt(Optional.of(authorizationUser), authenticationUser, requestPassword);
                doPasswordAuthWithDelegation(authenticationAttempt, session, request, responder);
            }
        }
    }
}
