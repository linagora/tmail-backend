/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                  *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.webadmin.vault;

import java.util.Optional;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.core.MailAddress;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxNotFoundException;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.VaultConfiguration;
import org.apache.james.webadmin.vault.routes.RestoreService;

import com.github.fge.lambdas.Throwing;
import com.linagora.tmail.team.TeamMailbox;

import scala.jdk.javaapi.OptionConverters;

public class TeamMailboxRestoreService extends RestoreService {

    private final MailboxManager mailboxManager;
    private final VaultConfiguration vaultConfiguration;

    @Inject
    public TeamMailboxRestoreService(DeletedMessageVault deletedMessageVault,
                                      MailboxManager mailboxManager,
                                      VaultConfiguration vaultConfiguration) {
        super(deletedMessageVault, mailboxManager, vaultConfiguration);
        this.mailboxManager = mailboxManager;
        this.vaultConfiguration = vaultConfiguration;
    }

    @Override
    protected MessageManager restoreMailboxManager(MailboxSession session) throws MailboxException {
        Optional<TeamMailbox> maybeTeamMailbox = resolveTeamMailbox(session);
        if (maybeTeamMailbox.isPresent()) {
            TeamMailbox teamMailbox = maybeTeamMailbox.get();
            MailboxSession ownerSession = mailboxManager.createSystemSession(teamMailbox.owner());
            MailboxPath restorePath = teamMailbox.mailboxPath(vaultConfiguration.getRestoreLocation());
            try {
                return mailboxManager.getMailbox(restorePath, ownerSession);
            } catch (MailboxNotFoundException e) {
                return mailboxManager.createMailbox(restorePath, ownerSession)
                    .map(Throwing.<org.apache.james.mailbox.model.MailboxId, MessageManager>function(
                        id -> mailboxManager.getMailbox(id, ownerSession)).sneakyThrow())
                    .orElseThrow(() -> new RuntimeException("createMailbox " + restorePath.asString() + " returns an empty mailboxId"));
            }
        }
        return super.restoreMailboxManager(session);
    }

    private Optional<TeamMailbox> resolveTeamMailbox(MailboxSession session) {
        try {
            MailAddress mailAddress = new MailAddress(session.getUser().asString());
            return OptionConverters.toJava(TeamMailbox.asTeamMailbox(mailAddress));
        } catch (AddressException e) {
            return Optional.empty();
        }
    }
}
