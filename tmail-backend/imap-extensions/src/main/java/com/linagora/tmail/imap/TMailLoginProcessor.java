package com.linagora.tmail.imap;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.message.request.LoginRequest;
import org.apache.james.imap.processor.LoginProcessor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.metrics.api.MetricFactory;
import org.apache.james.util.MDCBuilder;

import com.google.inject.Inject;

public class TMailLoginProcessor extends LoginProcessor {
    private static final String DELEGATION_SPLIT_CHARACTER = "+";

    @Inject
    public TMailLoginProcessor(MailboxManager mailboxManager, StatusResponseFactory factory, MetricFactory metricFactory) {
        super(mailboxManager, factory, metricFactory);
    }

    @Override
    protected void processRequest(LoginRequest request, ImapSession session, Responder responder) {
        Username requestUserid = request.getUserid();
        String localPart = requestUserid.getLocalPart();
        if (!localPart.contains(DELEGATION_SPLIT_CHARACTER)) {
            super.processRequest(request, session, responder);
        } else {
            String delegatedUser = StringUtils.substringBefore(localPart, DELEGATION_SPLIT_CHARACTER);
            String ownerUser = StringUtils.substringAfter(localPart, DELEGATION_SPLIT_CHARACTER);

            if (StringUtils.isAnyEmpty(delegatedUser, ownerUser)) {
                no(request, responder, HumanReadableText.INVALID_LOGIN);
                return;
            }

            Username authenticationId = Username.of(delegatedUser).withDefaultDomain(requestUserid.getDomainPart());
            Username delegateUsername = Username.of(ownerUser).withDefaultDomain(requestUserid.getDomainPart());

            AuthenticationAttempt authenticationAttempt = new AuthenticationAttempt(Optional.of(delegateUsername), authenticationId, request.getPassword());
            doAuthWithDelegation(authenticationAttempt, session, request, responder);
        }
    }

    @Override
    protected MDCBuilder mdc(LoginRequest request) {
        return super.mdc(request);
    }
}
