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

package com.linagora.tmail.vault;

import java.time.Clock;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.blob.api.BlobId;
import org.apache.james.blob.api.BlobStore;
import org.apache.james.core.Username;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.SessionProvider;
import org.apache.james.mailbox.events.MailboxEvents.MessageContentDeletionEvent;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.vault.DeletedMessageVault;
import org.apache.james.vault.DeletedMessageVaultDeletionListener;

import com.linagora.tmail.team.TeamMailbox;

import reactor.core.publisher.Mono;
import scala.Option;

/**
 * A {@link DeletedMessageVaultDeletionListener} aware of TeamMailbox namespaces.
 *
 * <p>For mailboxes in the {@code #TeamMailbox} namespace, deleted messages are stored
 * in the vault under the team mailbox's {@code self} identity (e.g. {@code sales@domain.tld})
 * rather than the technical {@code team-mailbox@domain.tld} owner used internally.
 * This allows vault operations (restore, browse) to target each team mailbox individually.
 *
 * <p>Regular user mailboxes are handled identically to the base {@link DeletedMessageVaultDeletionListener}.
 */
public class TeamMailboxDeletedMessageVaultDeletionListener extends DeletedMessageVaultDeletionListener {

    @Inject
    public TeamMailboxDeletedMessageVaultDeletionListener(BlobId.Factory blobIdFactory,
                                                          DeletedMessageVault deletedMessageVault,
                                                          BlobStore blobStore,
                                                          Clock clock,
                                                          MessageIdManager messageIdManager,
                                                          SessionProvider sessionProvider) {
        super(blobIdFactory, deletedMessageVault, blobStore, clock, messageIdManager, sessionProvider);
    }

    @Override
    public Mono<Void> forMessage(MessageContentDeletionEvent event) {
        MessageContentDeletionEvent mappedEvent = resolveTeamMailboxUsername(event)
            .map(vaultUsername -> remapUsername(event, vaultUsername))
            .orElse(event);
        return super.forMessage(mappedEvent);
    }

    private Optional<Username> resolveTeamMailboxUsername(MessageContentDeletionEvent event) {
        return event.mailboxPath()
            .flatMap(MailboxPath::parseEscaped)
            .flatMap(path -> {
                Option<TeamMailbox> maybeTeamMailbox = TeamMailbox.from(path);
                if (maybeTeamMailbox.isDefined()) {
                    return Optional.of(maybeTeamMailbox.get().self());
                }
                return Optional.empty();
            });
    }

    private MessageContentDeletionEvent remapUsername(MessageContentDeletionEvent event, Username username) {
        if (username.equals(event.getUsername())) {
            return event;
        }
        return new MessageContentDeletionEvent(
            event.eventId(),
            username,
            event.mailboxId(),
            event.mailboxACL(),
            event.messageId(),
            event.size(),
            event.internalDate(),
            event.flags(),
            event.hasAttachments(),
            event.headerBlobId(),
            event.headerContent(),
            event.bodyBlobId(),
            event.mailboxPath());
    }
}
