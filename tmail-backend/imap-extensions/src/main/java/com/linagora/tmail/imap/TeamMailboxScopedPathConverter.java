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

import org.apache.commons.lang3.StringUtils;
import org.apache.james.core.Username;
import org.apache.james.imap.api.display.ModifiedUtf7;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.model.MailboxConstants;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.search.MailboxQuery;
import org.apache.james.mailbox.model.search.PrefixedRegex;

import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxNameSpace;

/**
 * A {@link org.apache.james.imap.main.PathConverter} that presents a single team mailbox as the root
 * layout of the session.
 *
 * Relative mailbox names (INBOX, Sent, ...) are rewritten into the {@code #TeamMailbox} subtree of the
 * scoped team mailbox and, conversely, team mailbox paths are echoed back as root-relative names. This
 * lets off-the-shelf migration tools copy the team mailbox folder-by-folder as if it were a personal
 * account. See <a href="https://github.com/linagora/tmail-backend/issues/2405">#2405</a>.
 *
 * Absolute {@code #TeamMailbox.} and {@code #user.} paths keep going through the regular
 * {@link TMailPathConverter} behaviour.
 */
public class TeamMailboxScopedPathConverter extends TMailPathConverter {
    private static final String TEAM_MAILBOX_NAMESPACE = TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE();
    private static final String USER_NAMESPACE = "#user";

    private final TeamMailbox teamMailbox;

    protected TeamMailboxScopedPathConverter(MailboxSession mailboxSession, TeamMailbox teamMailbox) {
        super(mailboxSession);
        this.teamMailbox = teamMailbox;
    }

    @Override
    public MailboxPath buildFullPath(String mailboxName) {
        if (referencesOtherNamespace(mailboxName)) {
            return super.buildFullPath(mailboxName);
        }
        String relative = stripPrivateNamespace(mailboxName);
        if (relative.isEmpty()) {
            return teamRootPath();
        }
        return scopedPath(relative);
    }

    @Override
    public Optional<String> mailboxName(boolean relative, MailboxPath path, MailboxSession session) {
        if (isWithinScopedTeam(path)) {
            String prefix = teamName() + session.getPathDelimiter();
            String name = path.getName();
            if (name.startsWith(prefix)) {
                return Optional.of(name.substring(prefix.length()));
            }
        }
        return super.mailboxName(relative, path, session);
    }

    @Override
    public MailboxQuery mailboxQuery(String finalReferencename, String mailboxName, ImapSession session) {
        if (referencesOtherNamespace(finalReferencename) || referencesOtherNamespace(mailboxName)) {
            return super.mailboxQuery(finalReferencename, mailboxName, session);
        }

        MailboxSession mailboxSession = session.getMailboxSession();
        char delimiter = mailboxSession.getPathDelimiter();
        String decodedMailboxName = ModifiedUtf7.decodeModifiedUTF7(mailboxName);

        String prefix = teamName() + delimiter;
        if (StringUtils.isNotEmpty(finalReferencename)) {
            prefix = prefix + stripPrivateNamespace(finalReferencename) + delimiter;
        }

        return MailboxQuery.builder()
            .userAndNamespaceFrom(teamRootPath())
            .expression(new PrefixedRegex(prefix, decodedMailboxName, delimiter))
            .build();
    }

    private boolean referencesOtherNamespace(String mailboxName) {
        return StringUtils.startsWithIgnoreCase(mailboxName, TEAM_MAILBOX_NAMESPACE)
            || StringUtils.startsWithIgnoreCase(mailboxName, USER_NAMESPACE);
    }

    private String stripPrivateNamespace(String mailboxName) {
        String privatePrefix = MailboxConstants.USER_NAMESPACE + mailboxSession.getPathDelimiter();
        if (StringUtils.startsWithIgnoreCase(mailboxName, privatePrefix)) {
            return mailboxName.substring(privatePrefix.length());
        }
        if (mailboxName.equalsIgnoreCase(MailboxConstants.USER_NAMESPACE)) {
            return "";
        }
        return mailboxName;
    }

    private boolean isWithinScopedTeam(MailboxPath path) {
        return path.getNamespace().equalsIgnoreCase(TEAM_MAILBOX_NAMESPACE)
            && path.getUser().equals(owner())
            && (path.getName().equals(teamName())
                || path.getName().startsWith(teamName() + mailboxSession.getPathDelimiter()));
    }

    private MailboxPath scopedPath(String relative) {
        return new MailboxPath(TEAM_MAILBOX_NAMESPACE, owner(), teamName() + mailboxSession.getPathDelimiter() + relative);
    }

    private MailboxPath teamRootPath() {
        return new MailboxPath(TEAM_MAILBOX_NAMESPACE, owner(), teamName());
    }

    private String teamName() {
        return teamMailbox.mailboxName().asString();
    }

    private Username owner() {
        return teamMailbox.owner();
    }
}
