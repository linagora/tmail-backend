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

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.mailbox.model.search.Wildcard;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;

public class TMailPathConverter implements PathConverter {
    protected final MailboxSession mailboxSession;
    protected final PathConverter defaultpathConverter;

    protected TMailPathConverter(MailboxSession mailboxSession) {
        this.mailboxSession = mailboxSession;
        this.defaultpathConverter = PathConverter.Factory.DEFAULT.forSession(mailboxSession);
    }

    public MailboxPath buildFullPath(String mailboxName) {
        if (StringUtils.startsWithIgnoreCase(mailboxName, TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())) {
            return getTeamMailboxPath(mailboxName);
        } else {
            return defaultpathConverter.buildFullPath(mailboxName);
        }
    }

    public Optional<String> mailboxName(boolean relative, MailboxPath path, MailboxSession session) {
        if (path.getNamespace().equalsIgnoreCase(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())) {
            return Optional.of(path.getNamespace() + session.getPathDelimiter() + path.getName());
        } else {
            return defaultpathConverter.mailboxName(relative, path, session);
        }
    }

    @Override
    public MailboxQuery mailboxQuery(String finalReferencename, String mailboxName, ImapSession session) {
        MailboxSession mailboxSession = session.getMailboxSession();
        String decodedMailboxName = ModifiedUtf7.decodeModifiedUTF7(mailboxName);

        if (StringUtils.startsWithIgnoreCase(finalReferencename, TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())) {
            MailboxPath teamMailboxPath = getTeamMailboxPath(finalReferencename);

            return MailboxQuery.builder()
                .userAndNamespaceFrom(teamMailboxPath)
                .expression(new PrefixedRegex(
                    teamMailboxPath.getName(),
                    decodedMailboxName,
                    mailboxSession.getPathDelimiter()))
                .build();
        }

        if (StringUtils.isEmpty(finalReferencename) && StringUtils.startsWithIgnoreCase(mailboxName, TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())) {
            MailboxPath teamMailboxPath = getTeamMailboxPath(mailboxName);

            if (StringUtils.equals(teamMailboxPath.getName(), "*")) {
                return MailboxQuery.builder()
                    .userAndNamespaceFrom(teamMailboxPath)
                    .expression(Wildcard.INSTANCE)
                    .build();
            }
            return MailboxQuery.builder()
                .userAndNamespaceFrom(teamMailboxPath)
                .expression(new PrefixedRegex("", teamMailboxPath.getName(), mailboxSession.getPathDelimiter()))
                .build();
        }

        return defaultpathConverter.mailboxQuery(finalReferencename, mailboxName, session);
    }

    protected MailboxPath getTeamMailboxPath(String absolutePath) {
        List<String> mailboxPathParts = Splitter.on(mailboxSession.getPathDelimiter()).splitToList(absolutePath);
        String mailboxName = Joiner.on(mailboxSession.getPathDelimiter()).join(Iterables.skip(mailboxPathParts, 1));
        return new MailboxPath(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE(), teamMailboxUsername(mailboxSession), mailboxName);
    }

    protected Username teamMailboxUsername(MailboxSession mailboxSession) {
        return Username.from(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), mailboxSession.getUser().getDomainPart().map(Domain::asString));
    }
}
