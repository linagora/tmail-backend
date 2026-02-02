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

package com.linagora.tmail.james.app;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;

import jakarta.mail.Flags;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.james.events.Group;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.SearchOptions;
import org.apache.james.mailbox.model.SearchQuery;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.search.ListeningMessageSearchIndex;

import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

// Required for CLI
public class FakeMessageSearchIndex extends ListeningMessageSearchIndex {

    public FakeMessageSearchIndex() {
        super(null, ImmutableSet.of(), null);
    }

    @Override
    public Mono<Void> add(MailboxSession session, Mailbox mailbox, MailboxMessage message) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> delete(MailboxSession session, MailboxId mailboxId, Collection<MessageUid> expungedUids) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> deleteAll(MailboxSession session, MailboxId mailboxId) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Void> update(MailboxSession session, MailboxId mailboxId, List<UpdatedFlags> updatedFlagsList) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Mono<Flags> retrieveIndexedFlags(Mailbox mailbox, MessageUid uid) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Group getDefaultGroup() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Flux<MessageUid> doSearch(MailboxSession session, Mailbox mailbox, SearchQuery searchQuery) throws MailboxException {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public Flux<MessageId> search(MailboxSession session, Collection<MailboxId> mailboxIds, SearchQuery searchQuery, SearchOptions searchOptions) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public EnumSet<MailboxManager.SearchCapabilities> getSupportedCapabilities(EnumSet<MailboxManager.MessageCapabilities> messageCapabilities) {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public ExecutionMode getExecutionMode() {
        throw new NotImplementedException("not implemented");
    }

    @Override
    public void postReindexing() {
        throw new NotImplementedException("not implemented");
    }
}