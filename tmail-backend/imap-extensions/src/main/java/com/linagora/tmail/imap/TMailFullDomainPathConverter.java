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

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.imap.main.PathConverter;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;

public class TMailFullDomainPathConverter extends TMailPathConverter implements PathConverter {
    protected final Escaper domainEscaper;

    protected TMailFullDomainPathConverter(MailboxSession mailboxSession) {
        super(mailboxSession);
        this.domainEscaper = Escapers.builder()
                .addEscape(mailboxSession.getPathDelimiter(), "__")
                .addEscape('_', "_-")
                .build();
    }

    public Optional<String> mailboxName(boolean relative, MailboxPath path, MailboxSession session) {
        if (path.getNamespace().equalsIgnoreCase(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())) {
            // Because internal and external representations of a team mailbox differ, we need to convert
            // the local path like MailboxPath(namespace="#Teammailbox", user="team-mailbox@<domain>", name="<teamXXX>/<folder>")
            // to the external representation '#TeamMailbox/<teamXXX>@<domain>/<folder>'.
            List<String> mailboxNameParts = Splitter.on(session.getPathDelimiter()).splitToList(path.getName());
            String team = mailboxNameParts.getFirst();

            Optional<Domain> userDomain = path.getUser().getDomainPart();
            if (userDomain.isEmpty()) {
                throw new IllegalArgumentException("The full domain version of the path converter does not allow missing domains!");
            }
            String domain = domainEscaper.escape(
                    path.getUser().getDomainPart().map(Domain::asString).get()
            );

            String folder = Joiner.on(session.getPathDelimiter()).join(Iterables.skip(mailboxNameParts, 1));

            String externalMailboxName = path.getNamespace() + session.getPathDelimiter() + team + "@" + domain;
            if (!folder.isEmpty()) {
                externalMailboxName += session.getPathDelimiter() + folder;
            }
            return Optional.of(externalMailboxName);
        } else {
            return defaultpathConverter.mailboxName(relative, path, session);
        }
    }

    /// Convert external representation like {@code '#TeamMailbox/<teamXXX>@<domain>/<folder>'} to local path
    /// {@code MailboxPath(namespace="#Teammailbox", user="team-mailbox@<domain>", name="<teamXXX>/<folder>")}.
    /// This is essentially the reverse option to {@link #mailboxName(boolean, MailboxPath, MailboxSession)}, but
    /// for external TeamMailbox representations specifically.
    protected MailboxPath getTeamMailboxPath(String externalMailboxName) {
        List<String> externalMailboxNameParts = Splitter.on(mailboxSession.getPathDelimiter())
                .splitToList(externalMailboxName);
        if (externalMailboxNameParts.size() < 2) {
            throw new IllegalArgumentException("Invalid single part external mailbox name.");
        }

        List<String> usernameDomain = Splitter.on("@").splitToList(externalMailboxNameParts.get(1));
        if (!externalMailboxNameParts.getFirst().equalsIgnoreCase(TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE())) {
            throw new IllegalArgumentException("Cannot get internal team mailbox path for something that is not a team mailbox!");
        }
        if (usernameDomain.size() < 2) {
            throw new IllegalArgumentException("The full domain version of the path converter does not allow missing domains!");
        }

        String escapedDomain = usernameDomain.get(1);
        String domain = escapedDomain.replace("__", String.valueOf(MailboxConstants.FOLDER_DELIMITER))
                .replace("_-", "_");
        String team = usernameDomain.getFirst();
        String folder = Joiner.on(mailboxSession.getPathDelimiter()).join(Iterables.skip(externalMailboxNameParts, 2));

        return new MailboxPath(
                TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE(),
                teamMailboxUsername(domain),
                team + (folder.isEmpty() ? "" : mailboxSession.getPathDelimiter() + folder)
        );
    }

    protected Username teamMailboxUsername(String domain) {
        return Username.from(TeamMailbox.TEAM_MAILBOX_LOCAL_PART(), Optional.of(domain));
    }
}
