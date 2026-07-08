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
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.exception.MailboxException;

import com.linagora.tmail.team.TeamMailbox;

/**
 * Helper carrying the "team mailbox scope" of a {@link MailboxSession}.
 *
 * When a user logs in with {@code member+teamMailbox@domain}, the session gets scoped to the
 * designated team mailbox: its layout is then presented as the root layout (INBOX, Sent, ...) so
 * off-the-shelf migration tools (imapsync and co.) that copy folders relative to root work
 * without learning the {@code #TeamMailbox.} prefix.
 *
 * The scope is resolved once, at login time, and stashed in the {@link MailboxSession} attributes
 * so that both {@code PathConverter.Factory#forSession} overloads can read it synchronously on
 * every subsequent command.
 */
public class TeamMailboxScope {
    private static final String SCOPE_ATTRIBUTE = "com.linagora.tmail.imap.teamMailboxScope";

    public static void scopeTo(MailboxSession session, TeamMailbox teamMailbox) {
        session.getAttributes().put(SCOPE_ATTRIBUTE, teamMailbox);
    }

    public static Optional<TeamMailbox> forSession(MailboxSession session) {
        return Optional.ofNullable(session.getAttributes().get(SCOPE_ATTRIBUTE))
            .map(TeamMailbox.class::cast);
    }

    /**
     * Authenticates {@code member} without user delegation and scopes the resulting session to the
     * given team mailbox. Called on the auth success path before {@code AbstractAuthProcessor}
     * provisions the INBOX, so provisioning already sees the team mailbox layout and never creates a
     * stray personal namespace.
     */
    public static MailboxSession scopedSession(MailboxManager mailboxManager, Username member, TeamMailbox teamMailbox) throws MailboxException {
        MailboxSession mailboxSession = mailboxManager.authenticate(member).withoutDelegation();
        scopeTo(mailboxSession, teamMailbox);
        return mailboxSession;
    }
}
