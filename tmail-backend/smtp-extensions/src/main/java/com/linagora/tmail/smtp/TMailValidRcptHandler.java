package com.linagora.tmail.smtp;

import jakarta.inject.Inject;

import org.apache.james.core.MailAddress;
import org.apache.james.domainlist.api.DomainList;
import org.apache.james.protocols.smtp.SMTPSession;
import org.apache.james.rrt.api.RecipientRewriteTable;
import org.apache.james.rrt.api.RecipientRewriteTableException;
import org.apache.james.smtpserver.fastfail.ValidRcptHandler;
import org.apache.james.user.api.UsersRepository;
import org.apache.james.user.api.UsersRepositoryException;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxRepository;

import reactor.core.publisher.Mono;

public class TMailValidRcptHandler extends ValidRcptHandler {
    private final TeamMailboxRepository teamMailboxRepository;

    @Inject
    public TMailValidRcptHandler(UsersRepository users,
                                 RecipientRewriteTable recipientRewriteTable,
                                 DomainList domains,
                                 TeamMailboxRepository teamMailboxRepository) {
        super(users, recipientRewriteTable, domains);
        this.teamMailboxRepository = teamMailboxRepository;
    }

    @Override
    public boolean isValidRecipient(SMTPSession session, MailAddress recipient) throws UsersRepositoryException, RecipientRewriteTableException {
        return super.isValidRecipient(session, recipient)
            || isATeamMailbox(recipient);
    }

    private boolean isATeamMailbox(MailAddress recipient) {
        return TeamMailbox.asTeamMailbox(recipient)
            .map(tm -> Mono.from(teamMailboxRepository.exists(tm)).block())
            .getOrElse(() -> false);
    }
}
