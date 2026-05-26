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

import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.imap.processor.AuthenticateProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Splitter;
import com.google.inject.Inject;

public class TMailAuthenticateProcessor extends AuthenticateProcessor {
    private static final String DELEGATION_SPLIT_CHARACTER = "+";
    private static final Logger LOGGER = LoggerFactory.getLogger(TMailAuthenticateProcessor.class);

    @Inject
    public TMailAuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory,
                                      MetricFactory metricFactory, PathConverter.Factory pathConverterFactory) {
        super(mailboxManager, factory, metricFactory, pathConverterFactory);
    }

    @Override
    protected void parseAndDoPlainAuth(String initialClientResponse, ImapSession session, ImapRequest request, Responder responder) {
        AuthenticationAttempt authenticationAttempt = parseDelegationAttempt(initialClientResponse);
        if (authenticationAttempt.isDelegation()) {
            doPasswordAuthWithDelegation(authenticationAttempt, session, request, responder);
        } else {
            doPasswordAuth(authenticationAttempt, session, request, responder);
        }
    }

    private AuthenticationAttempt parseDelegationAttempt(String initialClientResponse) {
        try {
            String decodedResponse = new String(Base64.getDecoder().decode(initialClientResponse));
            List<String> tokens = Splitter.on("\0").omitEmptyStrings().splitToList(decodedResponse);

            return switch (tokens.size()) {
                case 2 -> handleTwoTokens(tokens);
                case 3 -> delegation(Username.of(tokens.get(0)), Username.of(tokens.get(1)), tokens.get(2));
                default -> throw new IllegalArgumentException("Invalid number of tokens in AUTHENTICATE initial client response, expected 2 or 3 but got " + tokens.size());
            };
        } catch (Exception e) {
            LOGGER.info("Invalid syntax in AUTHENTICATE initial client response", e);
            return noDelegation(null, null);
        }
    }

    private AuthenticationAttempt handleTwoTokens(List<String> tokens) {
        Username user = Username.of(tokens.getFirst());
        String localPart = user.getLocalPart();
        if (!localPart.contains(DELEGATION_SPLIT_CHARACTER)) {
            return noDelegation(user, tokens.get(1));
        }
        String authenticationId = StringUtils.substringBefore(localPart, DELEGATION_SPLIT_CHARACTER);
        String authorizationId = StringUtils.substringAfter(localPart, DELEGATION_SPLIT_CHARACTER);
        Username authenticationUser = Username.of(authenticationId).withDefaultDomain(user.getDomainPart());
        Username authorizationUser = Username.of(authorizationId).withDefaultDomain(user.getDomainPart());
        return delegation(authorizationUser, authenticationUser, tokens.get(1));
    }
}
