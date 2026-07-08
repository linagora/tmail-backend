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

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.mailbox.MailboxSession;

import com.google.common.annotations.VisibleForTesting;
import com.linagora.tmail.team.TeamMailbox;

public class TMailPathConverterFactory implements PathConverter.Factory {
    @VisibleForTesting
    static Boolean IS_FULL_DOMAIN_ENABLED = Boolean.parseBoolean(System.getProperty("imap.teamMailbox.fullDomain.enabled", "false"));

    public PathConverter forSession(ImapSession session) {
        return forSession(session.getMailboxSession());
    }

    public PathConverter forSession(MailboxSession session) {
        Optional<TeamMailbox> teamMailboxScope = TeamMailboxScope.forSession(session);
        if (teamMailboxScope.isPresent()) {
            return teamMailboxScopedPathConverterForSession(session, teamMailboxScope.get());
        }
        if (IS_FULL_DOMAIN_ENABLED) {
            return fullDomainPathConverterForSession(session);
        } else {
            return normalPathConverterForSession(session);
        }
    }

    public TeamMailboxScopedPathConverter teamMailboxScopedPathConverterForSession(MailboxSession session, TeamMailbox teamMailbox) {
        return new TeamMailboxScopedPathConverter(session, teamMailbox);
    }

    public TMailPathConverter normalPathConverterForSession(MailboxSession session) {
        return new TMailPathConverter(session);
    }

    public TMailFullDomainPathConverter fullDomainPathConverterForSession(MailboxSession session) {
        return new TMailFullDomainPathConverter(session);
    }
}
