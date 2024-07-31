package com.linagora.tmail.imap;

import java.util.Base64;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.request.ImapRequest;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
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
    public TMailAuthenticateProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(mailboxManager, factory, metricFactory);
    }

    @Override
    protected void doPlainAuth(String initialClientResponse, ImapSession session, ImapRequest request, Responder responder) {
        AuthenticationAttempt authenticationAttempt = parseDelegationAttempt(initialClientResponse);
        if (authenticationAttempt.isDelegation()) {
            doAuthWithDelegation(authenticationAttempt, session, request, responder);
        } else {
            doAuth(authenticationAttempt, session, request, responder, HumanReadableText.AUTHENTICATION_FAILED);
        }
        session.stopDetectingCommandInjection();
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
        String authId = StringUtils.substringAfter(localPart, DELEGATION_SPLIT_CHARACTER);
        String delegateId = StringUtils.substringBefore(localPart, DELEGATION_SPLIT_CHARACTER);
        Username authUser = Username.of(authId).withDefaultDomain(user.getDomainPart());
        Username delegateUser = Username.of(delegateId).withDefaultDomain(user.getDomainPart());
        return delegation(authUser, delegateUser, tokens.get(1));
    }
}
