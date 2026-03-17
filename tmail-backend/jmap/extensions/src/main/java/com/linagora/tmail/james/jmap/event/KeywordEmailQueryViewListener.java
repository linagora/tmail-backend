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

package com.linagora.tmail.james.jmap.event;

import static org.apache.james.util.ReactorUtils.DEFAULT_CONCURRENCY;
import static org.apache.james.util.ReactorUtils.LOW_CONCURRENCY;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener.ReactiveGroupEventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MessageContentDeletionEvent;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.apache.james.util.FunctionalUtils;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.projections.ConcernedKeywordsExtractor;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.MailboxReadRightsResolver;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class KeywordEmailQueryViewListener implements ReactiveGroupEventListener {
    public static class KeywordEmailQueryViewListenerGroup extends Group {

    }

    private record KeywordSaveContext(MessageId messageId, ThreadId threadId, Instant receivedAt) {
    }

    private record KeywordDeleteContext(MessageId messageId, Instant receivedAt) {
    }

    private static final Group GROUP = new KeywordEmailQueryViewListenerGroup();
    private static final int MESSAGES_BATCH_SIZE = 32;

    private final KeywordEmailQueryView keywordEmailQueryView;
    private final MailboxManager mailboxManager;
    private final MessageIdManager messageIdManager;
    private final MailboxReadRightsResolver mailboxReadRightsResolver;
    private final ConcernedKeywordsExtractor concernedKeywordsExtractor;

    @Inject
    public KeywordEmailQueryViewListener(KeywordEmailQueryView keywordEmailQueryView,
                                         MailboxManager mailboxManager,
                                         MessageIdManager messageIdManager,
                                         MailboxReadRightsResolver mailboxReadRightsResolver,
                                         ConcernedKeywordsExtractor concernedKeywordsExtractor) {
        this.keywordEmailQueryView = keywordEmailQueryView;
        this.mailboxManager = mailboxManager;
        this.messageIdManager = messageIdManager;
        this.mailboxReadRightsResolver = mailboxReadRightsResolver;
        this.concernedKeywordsExtractor = concernedKeywordsExtractor;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof Added
            || event instanceof FlagsUpdated
            || event instanceof MailboxACLUpdated
            || event instanceof MessageContentDeletionEvent;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        if (event instanceof Added added) {
            return handleAdded(added);
        }
        if (event instanceof FlagsUpdated flagsUpdated) {
            return handleFlagsUpdated(flagsUpdated);
        }
        if (event instanceof MailboxACLUpdated mailboxACLUpdated) {
            return handleMailboxACLUpdated(mailboxACLUpdated);
        }
        if (event instanceof MessageContentDeletionEvent messageContentDeletionEvent) {
            return handleMessageContentDeletion(messageContentDeletionEvent);
        }
        return Mono.empty();
    }

    private Mono<Void> handleAdded(Added added) {
        if (!hasConcernedKeywords(added)) {
            return Mono.empty();
        }

        return Mono.when(deleteKeywordViewIfNeeded(added), addKeywordView(added))
            .then();
    }

    private Mono<Void> deleteKeywordViewIfNeeded(Added added) {
        if (!added.isMoved()) {
            return Mono.empty();
        }

        MailboxSession actorSession = mailboxManager.createSystemSession(added.getUsername());
        MailboxId movedFromMailboxId = added.movedFromMailboxId().orElseThrow();

        return Mono.from(mailboxManager.getMailboxReactive(movedFromMailboxId, actorSession))
            .flatMap(sourceMailbox -> {
                Username sourceMailboxOwner = sourceMailbox.getMailboxPath().getUser();
                MailboxSession sourceOwnerSession = mailboxManager.createSystemSession(sourceMailboxOwner);

                return Mono.from(mailboxManager.getMailboxReactive(movedFromMailboxId, sourceOwnerSession))
                    .flatMap(mailbox -> mailboxReadRightsResolver.usersHavingReadRight(sourceMailboxOwner, mailbox, sourceOwnerSession)
                        .concatMap(username -> resolveInaccessibleMessages(added.getAdded().values(), username)
                            .flatMap(messageMetaData -> deleteKeywords(username, messageMetaData.getFlags(), messageMetaData.getInternalDate().toInstant(), messageMetaData.getMessageId()), LOW_CONCURRENCY))
                        .then());
            });
    }

    private Mono<Void> addKeywordView(Added added) {
        Username mailboxOwner = added.getMailboxPath().getUser();
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailboxOwner);

        return Mono.from(mailboxManager.getMailboxReactive(added.getMailboxId(), ownerSession))
            .flatMap(mailbox -> mailboxReadRightsResolver.usersHavingReadRight(mailboxOwner, mailbox, ownerSession)
                .concatMap(username -> Flux.fromIterable(added.getAdded().values())
                    .flatMap(messageMetaData -> saveKeywords(username, messageMetaData), LOW_CONCURRENCY))
                .then());
    }

    private Mono<Void> handleFlagsUpdated(FlagsUpdated flagsUpdated) {
        if (!hasConcernedKeywordChanges(flagsUpdated)) {
            return Mono.empty();
        }

        Username mailboxOwner = flagsUpdated.getMailboxPath().getUser();
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailboxOwner);

        return Mono.from(mailboxManager.getMailboxReactive(flagsUpdated.getMailboxId(), ownerSession))
            .flatMap(mailbox -> mailboxReadRightsResolver.usersHavingReadRight(mailboxOwner, mailbox, ownerSession)
                .concatMap(username -> Flux.fromIterable(flagsUpdated.getUpdatedFlags())
                    .flatMap(updatedFlags -> applyUpdatedFlags(username, mailbox, ownerSession, updatedFlags), LOW_CONCURRENCY))
                .then());
    }

    private Mono<Void> handleMailboxACLUpdated(MailboxACLUpdated mailboxACLUpdated) {
        Flux<Void> handleReadRightAdded = usersWhoGainedReadRight(mailboxACLUpdated.getUsername(), mailboxACLUpdated.getAclDiff())
            .concatMap(username -> indexKeywordViewForMailbox(mailboxACLUpdated.getMailboxId(), username));

        Flux<Void> handleReadRightRevoked = usersWhoLostReadRight(mailboxACLUpdated.getUsername(), mailboxACLUpdated.getAclDiff())
            .concatMap(username -> clearKeywordViewForMailbox(mailboxACLUpdated.getUsername(), mailboxACLUpdated.getMailboxId(), username));

        return Flux.merge(handleReadRightAdded, handleReadRightRevoked)
            .then();
    }

    private Mono<Void> handleMessageContentDeletion(MessageContentDeletionEvent messageContentDeletionEvent) {
        return deleteKeywords(messageContentDeletionEvent.getUsername(),
            messageContentDeletionEvent.flags(),
            messageContentDeletionEvent.internalDate(),
            messageContentDeletionEvent.messageId());
    }

    private Mono<Void> applyUpdatedFlags(Username username, MessageManager messageManager, MailboxSession session, UpdatedFlags updatedFlags) {
        Set<Keyword> oldKeywords = concernedKeywordsExtractor.extract(updatedFlags.getOldFlags());
        Set<Keyword> newKeywords = concernedKeywordsExtractor.extract(updatedFlags.getNewFlags());
        Set<Keyword> keywordsToDelete = subtract(oldKeywords, newKeywords);
        Set<Keyword> keywordsToAdd = subtract(newKeywords, oldKeywords);

        return Mono.when(resolveKeywordsAdditionOperation(username, messageManager, session, updatedFlags, keywordsToAdd),
                resolveKeywordsRemovalOperation(username, messageManager, session, updatedFlags, keywordsToDelete))
            .then();
    }

    private Mono<Void> resolveKeywordsRemovalOperation(Username username, MessageManager messageManager, MailboxSession session,
                                                       UpdatedFlags updatedFlags, Set<Keyword> keywordsToDelete) {
        if (keywordsToDelete.isEmpty()) {
            return Mono.empty();
        }

        return resolveKeywordDeleteContext(updatedFlags, messageManager, session)
            .flatMap(keywordDeleteContext -> deleteKeywords(username, keywordsToDelete, keywordDeleteContext.receivedAt(), keywordDeleteContext.messageId()));
    }

    private Mono<Void> resolveKeywordsAdditionOperation(Username username, MessageManager messageManager, MailboxSession session,
                                                        UpdatedFlags updatedFlags, Set<Keyword> keywordsToAdd) {
        if (keywordsToAdd.isEmpty()) {
            return Mono.empty();
        }

        return resolveKeywordSaveContext(updatedFlags, messageManager, session)
            .flatMap(keywordSaveContext -> saveKeywords(username, keywordsToAdd, keywordSaveContext));
    }

    private Mono<KeywordDeleteContext> resolveKeywordDeleteContext(UpdatedFlags updatedFlags, MessageManager messageManager, MailboxSession session) {
        if (updatedFlags.getMessageId().isPresent() && updatedFlags.getInternalDate().isPresent()) {
            return Mono.just(new KeywordDeleteContext(updatedFlags.getMessageId().get(), updatedFlags.getInternalDate().get().toInstant()));
        }

        // fallback to fetch message metadata if not present in the FlagsUpdated event
        return Flux.from(messageManager.getMessagesReactive(MessageRange.one(updatedFlags.getUid()), FetchGroup.MINIMAL, session))
            .next()
            .map(this::toDeleteContext);
    }

    private Mono<KeywordSaveContext> resolveKeywordSaveContext(UpdatedFlags updatedFlags, MessageManager messageManager, MailboxSession session) {
        return Flux.from(messageManager.getMessagesReactive(MessageRange.one(updatedFlags.getUid()), FetchGroup.MINIMAL, session))
            .next()
            .map(this::toSaveContext);
    }

    private Mono<Void> saveKeywords(Username username, MessageMetaData messageMetaData) {
        return saveKeywords(username,
            concernedKeywordsExtractor.extract(messageMetaData.getFlags()),
            new KeywordSaveContext(messageMetaData.getMessageId(), messageMetaData.getThreadId(), messageMetaData.getInternalDate().toInstant()));
    }

    private Mono<Void> saveKeywords(Username username, Set<Keyword> keywords, KeywordSaveContext messageContext) {
        return Flux.fromIterable(keywords)
            .flatMap(keyword -> keywordEmailQueryView.save(username, keyword, messageContext.receivedAt(), messageContext.messageId(), messageContext.threadId()), DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<Void> deleteKeywords(Username username, Flags flags, Instant receivedAt, MessageId messageId) {
        return deleteKeywords(username, concernedKeywordsExtractor.extract(flags), receivedAt, messageId);
    }

    private Mono<Void> deleteKeywords(Username username, Set<Keyword> keywords, Instant receivedAt, MessageId messageId) {
        return Flux.fromIterable(keywords)
            .flatMap(keyword -> keywordEmailQueryView.delete(username, keyword, receivedAt, messageId), DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<Void> indexKeywordViewForMailbox(MailboxId mailboxId, Username sharee) {
        MailboxSession shareeSession = mailboxManager.createSystemSession(sharee);

        return getMessagesInSharedMailbox(mailboxId, shareeSession)
            .flatMap(messageResult -> saveKeywords(sharee, concernedKeywordsExtractor.extract(messageResult.getFlags()), toSaveContext(messageResult)), LOW_CONCURRENCY)
            .then();
    }

    private Mono<Void> clearKeywordViewForMailbox(Username mailboxOwner, MailboxId mailboxId, Username sharee) {
        MailboxSession ownerSession = mailboxManager.createSystemSession(mailboxOwner);
        MailboxSession shareeSession = mailboxManager.createSystemSession(sharee);

        return getMessagesInSharedMailbox(mailboxId, ownerSession)
            .window(MESSAGES_BATCH_SIZE)
            .concatMap(window -> window.collectList()
                .flatMapMany(messageResults -> resolveInaccessibleMessages(messageResults, shareeSession))
                .flatMap(messageResult -> deleteKeywords(sharee, messageResult.getFlags(), messageResult.getInternalDate().toInstant(), messageResult.getMessageId()), LOW_CONCURRENCY))
            .then();
    }

    private Flux<MessageResult> getMessagesInSharedMailbox(MailboxId mailboxId, MailboxSession ownerSession) {
        return Mono.from(mailboxManager.getMailboxReactive(mailboxId, ownerSession))
            .flatMapMany(mailbox -> Flux.from(mailbox.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, ownerSession)));
    }

    private Flux<MessageResult> resolveInaccessibleMessages(List<MessageResult> messageResults, MailboxSession shareeSession) {
        return Mono.from(messageIdManager.accessibleMessagesReactive(messageResults.stream()
                .map(MessageResult::getMessageId)
                .collect(ImmutableSet.toImmutableSet()), shareeSession))
            .flatMapMany(accessibleMessageIds -> Flux.fromIterable(messageResults)
                .filter(messageResult -> !accessibleMessageIds.contains(messageResult.getMessageId())));
    }

    private Flux<MessageMetaData> resolveInaccessibleMessages(Collection<MessageMetaData> messageMetaData, Username username) {
        MailboxSession session = mailboxManager.createSystemSession(username);

        return Mono.from(messageIdManager.accessibleMessagesReactive(messageMetaData.stream()
                .map(MessageMetaData::getMessageId)
                .collect(ImmutableSet.toImmutableSet()), session))
            .flatMapMany(accessibleMessageIds -> Flux.fromIterable(messageMetaData)
                .filter(metadata -> !accessibleMessageIds.contains(metadata.getMessageId())));
    }

    private KeywordSaveContext toSaveContext(MessageResult messageResult) {
        return new KeywordSaveContext(messageResult.getMessageId(), messageResult.getThreadId(), messageResult.getInternalDate().toInstant());
    }

    private KeywordDeleteContext toDeleteContext(MessageResult messageResult) {
        return new KeywordDeleteContext(messageResult.getMessageId(), messageResult.getInternalDate().toInstant());
    }

    private Flux<Username> usersWhoGainedReadRight(Username owner, ACLDiff aclDiff) {
        return impactedUsers(aclDiff)
            .filterWhen(username -> mailboxReadRightsResolver.hasReadRight(owner, aclDiff.getOldACL(), username)
                .map(FunctionalUtils.negate()))
            .filterWhen(username -> mailboxReadRightsResolver.hasReadRight(owner, aclDiff.getNewACL(), username));
    }

    private Flux<Username> usersWhoLostReadRight(Username owner, ACLDiff aclDiff) {
        return impactedUsers(aclDiff)
            .filterWhen(username -> mailboxReadRightsResolver.hasReadRight(owner, aclDiff.getOldACL(), username))
            .filterWhen(username -> mailboxReadRightsResolver.hasReadRight(owner, aclDiff.getNewACL(), username)
                .map(FunctionalUtils.negate()));
    }

    private Flux<Username> impactedUsers(ACLDiff aclDiff) {
        return Flux.concat(
                Flux.fromIterable(aclDiff.getOldACL().getEntries().keySet()),
                Flux.fromIterable(aclDiff.getNewACL().getEntries().keySet()))
            .filter(entryKey -> MailboxACL.NameType.user.equals(entryKey.getNameType()))
            .map(MailboxACL.EntryKey::getName)
            .map(Username::of)
            .distinct();
    }

    private boolean hasConcernedKeywords(Added added) {
        return added.getAdded().values()
            .stream()
            .map(MessageMetaData::getFlags)
            .map(concernedKeywordsExtractor::extract)
            .anyMatch(keywords -> !keywords.isEmpty());
    }

    private boolean hasConcernedKeywordChanges(FlagsUpdated flagsUpdated) {
        return flagsUpdated.getUpdatedFlags()
            .stream()
            .anyMatch(this::hasConcernedKeywordChanges);
    }

    private boolean hasConcernedKeywordChanges(UpdatedFlags updatedFlags) {
        return concernedKeywordsExtractor.hasChanges(updatedFlags.getOldFlags(), updatedFlags.getNewFlags());
    }

    private Set<Keyword> subtract(Set<Keyword> base, Set<Keyword> toSubtract) {
        // return the keyword that only exists in base but not in toSubtract
        return base.stream()
            .filter(keyword -> !toSubtract.contains(keyword))
            .collect(ImmutableSet.toImmutableSet());
    }
}
