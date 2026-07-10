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
import org.apache.james.imap.main.PathConverter;
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
 * Absolute {@code #TeamMailbox.} and {@code #user.} paths are handed over to the {@code delegate}, the
 * converter the session would have used had it not been scoped. Scoping thus never alters the way
 * absolute paths are read or rendered, be it the regular or the full domain flavour.
 */
public class TeamMailboxScopedPathConverter implements PathConverter {
    private static final String TEAM_MAILBOX_NAMESPACE = TeamMailboxNameSpace.TEAM_MAILBOX_NAMESPACE();
    private static final String USER_NAMESPACE = "#user";

    private final MailboxSession mailboxSession;
    private final TeamMailbox teamMailbox;
    private final PathConverter delegate;

    protected TeamMailboxScopedPathConverter(MailboxSession mailboxSession, TeamMailbox teamMailbox, PathConverter delegate) {
        this.mailboxSession = mailboxSession;
        this.teamMailbox = teamMailbox;
        this.delegate = delegate;
    }

    @Override
    public MailboxPath buildFullPath(String mailboxName) {
        if (referencesOtherNamespace(mailboxName)) {
            return delegate.buildFullPath(mailboxName);
        }
        String relative = stripPrivateNamespace(mailboxName);
        if (relative.isEmpty()) {
            return teamRootPath();
        }
        return scopedPath(sanitizeMailboxName(relative));
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
        return delegate.mailboxName(relative, path, session);
    }

    @Override
    public MailboxQuery mailboxQuery(String finalReferencename, String mailboxName, ImapSession session) {
        if (referencesOtherNamespace(finalReferencename) || referencesOtherNamespace(mailboxName)) {
            return delegate.mailboxQuery(finalReferencename, mailboxName, session);
        }

        MailboxSession mailboxSession = session.getMailboxSession();
        char delimiter = mailboxSession.getPathDelimiter();
        String decodedMailboxName = ModifiedUtf7.decodeModifiedUTF7(mailboxName);

        // No delimiter is appended after the reference name: IMAP concatenates the reference and the
        // pattern verbatim, and PrefixedRegex already skips the delimiter that follows its prefix.
        String prefix = teamName() + delimiter;
        if (StringUtils.isNotEmpty(finalReferencename)) {
            prefix = prefix + stripPrivateNamespace(ModifiedUtf7.decodeModifiedUTF7(finalReferencename));
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

    /**
     * INBOX is the one case insensitive mailbox name of IMAP (see IMAP-349), which James' default
     * converter canonicalizes. The scoped root layout is a mailbox layout: it must do so too.
     */
    private String sanitizeMailboxName(String mailboxName) {
        if (mailboxName.equalsIgnoreCase(MailboxConstants.INBOX)) {
            return MailboxConstants.INBOX;
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
