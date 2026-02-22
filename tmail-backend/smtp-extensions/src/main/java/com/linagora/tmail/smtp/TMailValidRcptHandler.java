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
        MailAddress strippedRecipient = recipient.stripDetails(UsersRepository.LOCALPART_DETAIL_DELIMITER);
        return TeamMailbox.asTeamMailbox(strippedRecipient)
            .map(tm -> Mono.from(teamMailboxRepository.exists(tm)).block())
            .getOrElse(() -> false);
    }
}
