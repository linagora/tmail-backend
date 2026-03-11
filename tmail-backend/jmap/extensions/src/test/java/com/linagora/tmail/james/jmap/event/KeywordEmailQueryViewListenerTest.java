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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.ONE_HUNDRED_MILLISECONDS;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import jakarta.mail.Flags;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.util.streams.Limit;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.MemoryKeywordEmailQueryView;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class KeywordEmailQueryViewListenerTest {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility.with()
        .pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .atMost(Durations.TEN_SECONDS)
        .await();

    private static final Username OWNER = Username.of("owner@domain.tld");
    private static final Username SHAREE = Username.of("sharee@domain.tld");
    private static final MailboxPath OWNER_INBOX = MailboxPath.inbox(OWNER);
    private static final Keyword IMPORTANT_FLAGGED = new Keyword("$flagged");
    private static final Keyword SEEN = new Keyword("$seen");
    private static final Keyword USER_NEW_KEYWORD = new Keyword("project-a");
    private static final Keyword USER_OLD_KEYWORD = new Keyword("old-tag");
    private static final Instant INTERNAL_DATE = Instant.parse("2024-01-01T10:15:30Z");

    private MemoryKeywordEmailQueryView keywordEmailQueryView;
    private InMemoryIntegrationResources resources;
    private MailboxManager mailboxManager;
    private MailboxSession ownerSession;
    private MailboxId mailboxId;
    private KeywordEmailQueryViewListener testee;

    @BeforeEach
    void setUp() {
        resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        ownerSession = mailboxManager.createSystemSession(OWNER);

        keywordEmailQueryView = new MemoryKeywordEmailQueryView();
        testee = new KeywordEmailQueryViewListener(keywordEmailQueryView, mailboxManager, new UnionMailboxACLResolver());
        resources.getEventBus().register(testee);

        mailboxId = Mono.from(mailboxManager.createMailboxReactive(OWNER_INBOX, ownerSession)).block();
    }

    @Nested
    class AddedEvent {
        @Test
        void shouldPopulateConcernedKeywords() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED), USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldIgnoreMovedMessages() {
            MessageId messageId = resources.getMessageIdFactory().generate();
            SortedMap<MessageUid, MessageMetaData> addedMessages = new TreeMap<>();
            addedMessages.put(MessageUid.of(1L), new MessageMetaData(MessageUid.of(1L), ModSeq.first(),
                asFlags(List.of(Flags.Flag.FLAGGED), USER_NEW_KEYWORD), 42L, Date.from(INTERNAL_DATE), Optional.empty(),
                messageId, ThreadId.fromBaseMessageId(messageId)));

            Added moved = new Added(MailboxSession.SessionId.of(1L), OWNER, OWNER_INBOX, mailboxId, addedMessages,
                Event.EventId.random(), Added.IS_DELIVERY, Added.IS_APPENDED, Optional.of(TestId.of(999L)));

            Mono.from(testee.reactiveEvent(moved)).block();

            assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).isEmpty();
            assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).isEmpty();
        }

        @Test
        void shouldIndexFlaggedAsFlaggedKeyword() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(new Flags(Flags.Flag.FLAGGED));
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED))
                .containsExactly(messageId));
        }

        @Test
        void shouldIgnoreNonConcernedSystemFlags() throws Exception {
            appendMessage(new Flags(Flags.Flag.SEEN));

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(OWNER, SEEN))
                .isEmpty());
        }

        @Test
        void shouldOnlyIndexConcernedKeywordsWhenConcernedAndNonConcernedFlagsAreMixed() throws Exception {
            MessageId messageId = appendMessage(asFlags(List.of(Flags.Flag.SEEN, Flags.Flag.FLAGGED), USER_NEW_KEYWORD)).getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, SEEN)).isEmpty();
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }
    }

    @Nested
    class FlagsUpdatedEvent {
        @Test
        void shouldUpdateKeywordViewWhenFlagsReplaceMode() throws Exception {
            // GIVEN a message with an old keyword
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_OLD_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(OWNER, USER_OLD_KEYWORD)).containsExactly(messageId));

            // WHEN we replace flags: seen + flagged + new keyword
            ownerInbox().setFlags(asFlags(List.of(Flags.Flag.SEEN, Flags.Flag.FLAGGED), USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.one(uid), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_OLD_KEYWORD)).isEmpty(); // Old keyword should be removed
                assertThat(messageIdsByKeywordView(OWNER, SEEN)).isEmpty(); // Non-concerned system flag should be ignored
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldAddConcernedKeywordViewWhenFlagsAddMode() throws Exception {
            // GIVEN a message without any concerned keyword
            MessageManager.AppendResult appendResult = appendMessage(new Flags());
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            // WHEN we add flags: seen + flagged + new keyword
            ownerInbox().setFlags(asFlags(List.of(Flags.Flag.SEEN, Flags.Flag.FLAGGED), USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.ADD, MessageRange.one(uid), ownerSession);
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED))
                .containsExactly(messageId));

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, SEEN)).isEmpty(); // Non-concerned system flag should be ignored
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldRemoveKeywordViewWhenFlagsRemoveMode() throws Exception {
            // GIVEN a message with concerned keywords: flagged + user keyword
            MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED), USER_NEW_KEYWORD));
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(appendResult.getId().getMessageId());
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(appendResult.getId().getMessageId());
            });

            // WHEN we remove flagged system flag only
            ownerInbox().setFlags(asFlags(List.of(Flags.Flag.FLAGGED)), MessageManager.FlagsUpdateMode.REMOVE, MessageRange.one(appendResult.getId().getUid()), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).isEmpty();
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(appendResult.getId().getMessageId());
            });
        }

        @Test
        void shouldNoopWhenFlagsNotChanged() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId));

            ownerInbox().setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.one(appendResult.getId().getUid()), ownerSession);
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId));
        }
    }

    @Nested
    class MailboxACLUpdatedEvent {
        @Test
        void shouldIndexKeywordViewForUsersWhoGainReadRightOnAMailbox() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();

            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read).asAddition(), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));
        }

        @Test
        void shouldDeleteKeywordViewForUsersWhoLoseReadRightOnAMailbox() throws Exception {
            // GIVEN a message with a user keyword and a sharee with read right on the mailbox
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read).asAddition(), ownerSession);
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));

            // WHEN we remove read right for the sharee
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read).asRemoval(), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .isEmpty());
        }

        @Test
        void shouldNoopForUsersGainingNonReadRights() throws Exception {
            appendMessage(asFlags(USER_NEW_KEYWORD));

            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Write).asAddition(), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .isEmpty());
        }

        @Test
        void shouldDeleteKeywordViewForUsersWhoGainNegativeACLReadRight() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read).asAddition(), ownerSession);
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));

            // WHEN we add a negative ACL for the sharee
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command()
                    .key(MailboxACL.EntryKey.createUserEntryKey(SHAREE.asString(), MailboxACL.NEGATIVE_KEY))
                    .rights(MailboxACL.Right.Read)
                    .asAddition(),
                ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .isEmpty());
        }
    }

    @Nested
    class MessageContentDeletionEvent {
        @Test
        void shouldDeleteConcernedKeywords() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED), USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
            });

            MailboxEvents.MessageContentDeletionEvent event = new MailboxEvents.MessageContentDeletionEvent(Event.EventId.random(), OWNER, mailboxId, messageId, 12L,
                INTERNAL_DATE, asFlags(List.of(Flags.Flag.FLAGGED), USER_NEW_KEYWORD), false, Optional.empty(), Optional.empty(), "bodyBlobId");

            Mono.from(testee.reactiveEvent(event)).block();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).isEmpty();
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).isEmpty();
            });
        }
    }

    private MessageManager.AppendResult appendMessage(Flags flags) throws MailboxException {
        return ownerInbox().appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(Date.from(INTERNAL_DATE))
            .withFlags(flags)
            .notRecent()
            .build("Subject: test\r\n\r\nbody"), ownerSession);
    }

    private MessageManager ownerInbox() throws MailboxException {
        return mailboxManager.getMailbox(mailboxId, ownerSession);
    }

    private Flags asFlags(Keyword... flags) {
        Flags result = new Flags();
        for (Keyword flag : flags) {
            result.add(flag.getFlagName());
        }
        return result;
    }

    private Flags asFlags(List<Flags.Flag> systemFlags, Keyword... userFlags) {
        Flags result = new Flags();
        systemFlags.forEach(result::add);
        result.add(asFlags(userFlags));
        return result;
    }

    private List<MessageId> messageIdsByKeywordView(Username username, Keyword keyword) {
        return Flux.from(keywordEmailQueryView.listMessagesByKeyword(username, keyword, options()))
            .collectList()
            .block();
    }

    private KeywordEmailQueryView.Options options() {
        return new KeywordEmailQueryView.Options(Optional.empty(), Optional.empty(), Limit.limit(10), false);
    }
}
