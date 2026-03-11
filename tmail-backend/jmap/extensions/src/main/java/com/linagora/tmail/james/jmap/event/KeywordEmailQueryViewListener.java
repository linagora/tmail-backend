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

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener.ReactiveGroupEventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.acl.ACLDiff;
import org.apache.james.mailbox.acl.MailboxACLResolver;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MailboxEvents.FlagsUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MailboxACLUpdated;
import org.apache.james.mailbox.events.MailboxEvents.MessageContentDeletionEvent;
import org.apache.james.mailbox.exception.UnsupportedRightException;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UpdatedFlags;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableSet;
import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;

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
    private static final Keyword FLAGGED = new Keyword("$flagged");

    private final KeywordEmailQueryView keywordEmailQueryView;
    private final MailboxManager mailboxManager;
    private final MailboxACLResolver mailboxACLResolver;

    @Inject
    public KeywordEmailQueryViewListener(KeywordEmailQueryView keywordEmailQueryView,
                                         MailboxManager mailboxManager,
                                         MailboxACLResolver mailboxACLResolver) {
        this.keywordEmailQueryView = keywordEmailQueryView;
        this.mailboxManager = mailboxManager;
        this.mailboxACLResolver = mailboxACLResolver;
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
        if (added.isMoved()) {
            return Mono.empty();
        }

        return Flux.fromIterable(added.getAdded().values())
            .flatMap(messageMetaData -> saveKeywords(added.getUsername(), messageMetaData))
            .then();
    }

    private Mono<Void> handleFlagsUpdated(FlagsUpdated flagsUpdated) {
        MailboxSession session = mailboxManager.createSystemSession(flagsUpdated.getUsername());

        return Mono.from(mailboxManager.getMailboxReactive(flagsUpdated.getMailboxId(), session))
            .flatMap(mailbox -> Flux.fromIterable(flagsUpdated.getUpdatedFlags())
                .flatMap(updatedFlags -> applyUpdatedFlags(flagsUpdated.getUsername(), mailbox, session, updatedFlags))
                .then());
    }

    private Mono<Void> handleMailboxACLUpdated(MailboxACLUpdated mailboxACLUpdated) {
        Flux<Void> handleReadRightAdded = Flux.fromIterable(usersWhoGainedReadRight(mailboxACLUpdated.getUsername(), mailboxACLUpdated.getAclDiff()))
            .concatMap(username -> indexKeywordViewForMailbox(mailboxACLUpdated.getUsername(), mailboxACLUpdated.getMailboxId(), username));

        Flux<Void> handleReadRightRevoked = Flux.fromIterable(usersWhoLostReadRight(mailboxACLUpdated.getUsername(), mailboxACLUpdated.getAclDiff()))
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
        Set<Keyword> oldKeywords = concernedKeywords(updatedFlags.getOldFlags());
        Set<Keyword> newKeywords = concernedKeywords(updatedFlags.getNewFlags());
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
            concernedKeywords(messageMetaData.getFlags()),
            new KeywordSaveContext(messageMetaData.getMessageId(), messageMetaData.getThreadId(), messageMetaData.getInternalDate().toInstant()));
    }

    private Mono<Void> saveKeywords(Username username, Set<Keyword> keywords, KeywordSaveContext messageContext) {
        return Flux.fromIterable(keywords)
            .flatMap(keyword -> keywordEmailQueryView.save(username, keyword, messageContext.receivedAt(), messageContext.messageId(), messageContext.threadId()))
            .then();
    }

    private Mono<Void> deleteKeywords(Username username, Flags flags, Instant receivedAt, MessageId messageId) {
        return deleteKeywords(username, concernedKeywords(flags), receivedAt, messageId);
    }

    private Mono<Void> deleteKeywords(Username username, Set<Keyword> keywords, Instant receivedAt, MessageId messageId) {
        return Flux.fromIterable(keywords)
            .flatMap(keyword -> keywordEmailQueryView.delete(username, keyword, receivedAt, messageId))
            .then();
    }

    private Mono<Void> indexKeywordViewForMailbox(Username mailboxOwner, MailboxId mailboxId, Username sharee) {
        MailboxSession session = mailboxManager.createSystemSession(mailboxOwner);

        return Mono.from(mailboxManager.getMailboxReactive(mailboxId, session))
            .flatMapMany(messageManager -> Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, session)))
            .flatMap(messageResult -> saveKeywords(sharee, concernedKeywords(messageResult.getFlags()), toSaveContext(messageResult)), DEFAULT_CONCURRENCY)
            .then();
    }

    private Mono<Void> clearKeywordViewForMailbox(Username mailboxOwner, MailboxId mailboxId, Username sharee) {
        MailboxSession session = mailboxManager.createSystemSession(mailboxOwner);

        return Mono.from(mailboxManager.getMailboxReactive(mailboxId, session))
            .flatMapMany(messageManager -> Flux.from(messageManager.getMessagesReactive(MessageRange.all(), FetchGroup.MINIMAL, session)))
            .flatMap(messageResult -> deleteKeywords(sharee, messageResult.getFlags(), messageResult.getInternalDate().toInstant(), messageResult.getMessageId()), DEFAULT_CONCURRENCY)
            .then();
    }

    private KeywordSaveContext toSaveContext(MessageResult messageResult) {
        return new KeywordSaveContext(messageResult.getMessageId(), messageResult.getThreadId(), messageResult.getInternalDate().toInstant());
    }

    private KeywordDeleteContext toDeleteContext(MessageResult messageResult) {
        return new KeywordDeleteContext(messageResult.getMessageId(), messageResult.getInternalDate().toInstant());
    }

    private Collection<Username> usersWhoGainedReadRight(Username owner, ACLDiff aclDiff) {
        return impactedUsers(aclDiff)
            .stream()
            .filter(username -> !hasReadRight(owner, aclDiff.getOldACL(), username) && hasReadRight(owner, aclDiff.getNewACL(), username))
            .collect(ImmutableSet.toImmutableSet());
    }

    private Collection<Username> usersWhoLostReadRight(Username owner, ACLDiff aclDiff) {
        return impactedUsers(aclDiff)
            .stream()
            .filter(username -> hasReadRight(owner, aclDiff.getOldACL(), username) && !hasReadRight(owner, aclDiff.getNewACL(), username))
            .collect(ImmutableSet.toImmutableSet());
    }

    private Collection<Username> impactedUsers(ACLDiff aclDiff) {
        return Stream.concat(aclDiff.getOldACL().getEntries().keySet().stream(), aclDiff.getNewACL().getEntries().keySet().stream())
            .filter(entryKey -> MailboxACL.NameType.user.equals(entryKey.getNameType()))
            .map(MailboxACL.EntryKey::getName)
            .map(Username::of)
            .collect(ImmutableSet.toImmutableSet());
    }

    private boolean hasReadRight(Username owner, MailboxACL acl, Username username) {
        try {
            return mailboxACLResolver.resolveRights(username, acl, owner).contains(MailboxACL.Right.Read);
        } catch (UnsupportedRightException e) {
            throw new IllegalStateException("Failed to resolve mailbox ACL rights", e);
        }
    }

    private Set<Keyword> concernedKeywords(Flags flags) {
        Stream<Keyword> concernSystemKeywords = flags.contains(Flags.Flag.FLAGGED) ? Stream.of(FLAGGED) : Stream.empty();
        Stream<Keyword> userKeywords = Arrays.stream(flags.getUserFlags())
            .map(flagName -> new Keyword(flagName.toLowerCase(Locale.US)));

        return Stream.concat(concernSystemKeywords, userKeywords)
            .collect(ImmutableSet.toImmutableSet());
    }

    private Set<Keyword> subtract(Set<Keyword> base, Set<Keyword> toSubtract) {
        // return the keyword that only exists in base but not in toSubtract
        return base.stream()
            .filter(keyword -> !toSubtract.contains(keyword))
            .collect(ImmutableSet.toImmutableSet());
    }
}
