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
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;
import org.apache.james.mailbox.model.search.Wildcard;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;

public class TMailPathConverter implements PathConverter {
    private static final String TEAM_DOMAIN_SEPARATOR = "@";

    protected final MailboxSession mailboxSession;
    protected final PathConverter defaultpathConverter;
    protected final Escaper domainEscaper;

    protected TMailPathConverter(MailboxSession mailboxSession) {
        this.mailboxSession = mailboxSession;
        this.defaultpathConverter = PathConverter.Factory.DEFAULT.forSession(mailboxSession);
        this.domainEscaper = Escapers.builder()
            .addEscape(mailboxSession.getPathDelimiter(), "__")
            .addEscape('_', "_-")
            .build();
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
            return teamMailboxName(path, session);
        } else {
            return defaultpathConverter.mailboxName(relative, path, session);
        }
    }

    // Team mailboxes of the session domain keep the short '#TeamMailbox.<team>' form. Team mailboxes of
    // another domain are qualified with their domain: '#TeamMailbox.<team>@<escaped domain>.<folder>'.
    private Optional<String> teamMailboxName(MailboxPath teamPath, MailboxSession session) {
        if (belongsToSessionDomain(teamPath, session)) {
            return Optional.of(teamPath.getNamespace() + session.getPathDelimiter() + teamPath.getName());
        }
        return teamPath.getUser().getDomainPart()
            .map(domain -> domainQualifiedTeamMailboxName(teamPath, domain, session));
    }

    private boolean belongsToSessionDomain(MailboxPath teamPath, MailboxSession session) {
        return teamPath.getUser().getDomainPart().equals(session.getUser().getDomainPart());
    }

    protected String domainQualifiedTeamMailboxName(MailboxPath teamPath, Domain domain, MailboxSession session) {
        List<String> mailboxNameParts = Splitter.on(session.getPathDelimiter()).splitToList(teamPath.getName());
        String team = mailboxNameParts.getFirst();
        String folder = Joiner.on(session.getPathDelimiter()).join(Iterables.skip(mailboxNameParts, 1));

        String qualifiedTeam = teamPath.getNamespace() + session.getPathDelimiter() + team + TEAM_DOMAIN_SEPARATOR + domainEscaper.escape(domain.asString());
        if (folder.isEmpty()) {
            return qualifiedTeam;
        }
        return qualifiedTeam + session.getPathDelimiter() + folder;
    }

    protected String unescapeDomain(String escapedDomain) {
        return escapedDomain.replace("__", String.valueOf(MailboxConstants.FOLDER_DELIMITER))
            .replace("_-", "_");
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
        List<String> teamAndDomain = Splitter.on(TEAM_DOMAIN_SEPARATOR).limit(2)
            .splitToList(Iterables.get(mailboxPathParts, 1, ""));
        if (teamAndDomain.size() == 2) {
            return domainQualifiedTeamMailboxPath(teamAndDomain.getFirst(), teamAndDomain.get(1), Iterables.skip(mailboxPathParts, 2));
        }
        String mailboxName = Joiner.on(mailboxSession.getPathDelimiter()).join(Iterables.skip(mailboxPathParts, 1));
        return new MailboxPath(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE(), teamMailboxUsername(mailboxSession), mailboxName);
    }

    // Team names can not contain '@': a team segment carrying one is qualified with the team domain.
    private MailboxPath domainQualifiedTeamMailboxPath(String team, String escapedDomain, Iterable<String> folderParts) {
        String mailboxName = Joiner.on(mailboxSession.getPathDelimiter()).join(Iterables.concat(List.of(team), folderParts));
        Username teamMailboxUsername = Username.from(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), Optional.of(unescapeDomain(escapedDomain)));
        return new MailboxPath(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE(), teamMailboxUsername, mailboxName);
    }

    protected Username teamMailboxUsername(MailboxSession mailboxSession) {
        return Username.from(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), mailboxSession.getUser().getDomainPart().map(Domain::asString));
    }
}
