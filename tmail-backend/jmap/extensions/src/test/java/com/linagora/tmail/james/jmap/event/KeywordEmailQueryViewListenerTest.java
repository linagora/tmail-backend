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
import java.util.Set;

import jakarta.mail.Flags;

import org.apache.james.core.Domain;
import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.jmap.mail.Keyword;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.UnionMailboxACLResolver;
import org.apache.james.mailbox.events.MailboxEvents;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxACL;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.store.StoreSubscriptionManager;
import org.apache.james.util.streams.Limit;
import org.awaitility.Awaitility;
import org.awaitility.Durations;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.linagora.tmail.james.jmap.projections.KeywordEmailQueryView;
import com.linagora.tmail.james.jmap.projections.MemoryKeywordEmailQueryView;
import com.linagora.tmail.team.TeamMailbox;
import com.linagora.tmail.team.TeamMailboxMember;
import com.linagora.tmail.team.TeamMailboxRepository;
import com.linagora.tmail.team.TeamMailboxRepositoryImpl;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import scala.jdk.javaapi.OptionConverters;

class KeywordEmailQueryViewListenerTest {
    private static final ConditionFactory CALMLY_AWAIT = Awaitility.with()
        .pollInterval(ONE_HUNDRED_MILLISECONDS)
        .and().pollDelay(ONE_HUNDRED_MILLISECONDS)
        .atMost(Durations.TEN_SECONDS)
        .await();

    private static final Domain DOMAIN = Domain.of("domain.tld");
    private static final Username OWNER = Username.of("owner@domain.tld");
    private static final Username SHAREE = Username.of("sharee@domain.tld");
    private static final Username SECOND_SHAREE = Username.of("second-sharee@domain.tld");
    private static final MailboxPath OWNER_INBOX = MailboxPath.inbox(OWNER);
    private static final Keyword IMPORTANT_FLAGGED = new Keyword("$flagged");
    private static final Keyword SEEN = new Keyword("$seen");
    private static final Keyword USER_NEW_KEYWORD = new Keyword("project-a");
    private static final Keyword USER_OLD_KEYWORD = new Keyword("old-tag");
    private static final String TEAM_MAILBOX_NAME = "marketing";
    private static final Instant INTERNAL_DATE = Instant.parse("2024-01-01T10:15:30Z");

    private MemoryKeywordEmailQueryView keywordEmailQueryView;
    private InMemoryIntegrationResources resources;
    private MailboxManager mailboxManager;
    private MailboxSession ownerSession;
    private MailboxId mailboxId;
    private KeywordEmailQueryViewListener testee;
    private TeamMailboxRepository teamMailboxRepository;

    @BeforeEach
    void setUp() {
        resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        ownerSession = mailboxManager.createSystemSession(OWNER);

        keywordEmailQueryView = new MemoryKeywordEmailQueryView();
        testee = new KeywordEmailQueryViewListener(keywordEmailQueryView, mailboxManager, resources.getMessageIdManager(), new UnionMailboxACLResolver());
        resources.getEventBus().register(testee);
        teamMailboxRepository = new TeamMailboxRepositoryImpl(mailboxManager,
            new StoreSubscriptionManager(resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getMapperFactory(), resources.getMailboxManager().getEventBus()),
            resources.getMailboxManager().getMapperFactory(),
            Set.of());

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
        void shouldPopulateConcernedKeywordsForOwnerAndShareeWhenOwnerAppendsInSharedMailbox() throws Exception {
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asAddition(), ownerSession);

            MessageId messageId = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED)))
                .getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });
        }

        @Test
        void shouldPopulateConcernedKeywordsForOwnerAndShareeWhenShareeAppendsInSharedMailbox() throws Exception {
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup, MailboxACL.Right.Insert)
                    .asAddition(),
                ownerSession);

            MessageId messageId = mailboxManager.getMailbox(mailboxId, shareeSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .withInternalDate(Date.from(INTERNAL_DATE))
                    .withFlags(asFlags(List.of(Flags.Flag.FLAGGED)))
                    .notRecent()
                    .build("Subject: shared append\r\n\r\nbody"), shareeSession)
                .getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });
        }

        @Test
        void shouldPopulateConcernedKeywordsForTeamMailboxMemberOnAddedEvent() throws Exception {
            TeamMailbox teamMailbox = createTeamMailbox(TEAM_MAILBOX_NAME);
            addMemberToTeamMailbox(teamMailbox, SHAREE);

            MessageId messageId = appendMessageToTeamInbox(teamMailbox, asFlags(List.of(Flags.Flag.FLAGGED), USER_NEW_KEYWORD))
                .getId()
                .getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }


        @Test
        void shouldPopulateKeywordViewForOwnerAndShareeWhenOwnerMovesMessageFromPersonalMailboxToSharedMailbox() throws Exception {
            MailboxId ownerPersonalMailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.forUser(OWNER, "private"), ownerSession)).block();

            // Owner shares the target mailbox with the sharee.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition(),
                ownerSession);

            // Owner appends the message in a personal mailbox first, so only owner sees it initially.
            MessageManager.AppendResult appendResult = mailboxManager.getMailbox(ownerPersonalMailboxId, ownerSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .withInternalDate(Date.from(INTERNAL_DATE))
                    .withFlags(asFlags(List.of(Flags.Flag.FLAGGED)))
                    .notRecent()
                    .build("Subject: private before share\r\n\r\nbody"), ownerSession);
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).isEmpty();
            });

            // Owner moves the message to the shared mailbox.
            Flux.from(mailboxManager.moveMessagesReactive(MessageRange.one(appendResult.getId().getUid()), ownerPersonalMailboxId, mailboxId, ownerSession))
                .then()
                .block();

            // Owner still sees the keyword-view entry, and sharee should now see it as the target mailbox is shared.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });
        }

        @Test
        void shouldPopulateKeywordViewForOwnerAndShareeWhenShareeMovesMessageFromPersonalMailboxToSharedMailbox() throws Exception {
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);
            MailboxId shareePersonalMailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.forUser(SHAREE, "private"), shareeSession)).block();

            // Owner shares the target mailbox with the sharee and allows inserts.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup, MailboxACL.Right.Insert)
                    .asAddition(),
                ownerSession);

            // Sharee appends the message in a personal mailbox first, so only sharee sees it initially.
            MessageManager.AppendResult appendResult = mailboxManager.getMailbox(shareePersonalMailboxId, shareeSession)
                .appendMessage(MessageManager.AppendCommand.builder()
                    .withInternalDate(Date.from(INTERNAL_DATE))
                    .withFlags(asFlags(List.of(Flags.Flag.FLAGGED)))
                    .notRecent()
                    .build("Subject: sharee private before share\r\n\r\nbody"), shareeSession);
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });

            // Sharee moves the message to the shared mailbox.
            Flux.from(mailboxManager.moveMessagesReactive(MessageRange.one(appendResult.getId().getUid()), shareePersonalMailboxId, mailboxId, shareeSession))
                .then()
                .block();

            // Sharee keeps the keyword-view entry, and owner should now see it as the target mailbox is shared.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });
        }

        @Test
        void shouldRemoveKeywordViewForOwnerAndKeepItForShareeWhenShareeMovesMessageFromSharedMailboxToPersonalMailbox() throws Exception {
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);
            MailboxId shareeMailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(SHAREE), shareeSession)).block();

            // Owner shares the mailbox with the sharee and appends a message with a concerned keyword.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup, MailboxACL.Right.PerformExpunge)
                    .asAddition(),
                ownerSession);
            MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED)));
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });

            // Sharee moves the message to his own mailbox.
            Flux.from(mailboxManager.moveMessagesReactive(MessageRange.one(appendResult.getId().getUid()), mailboxId, shareeMailboxId, shareeSession))
                .then()
                .block();

            // Owner should lose the keyword-view entry because the message is no longer in the shared mailbox,
            // but sharee should keep it because he still has access to the message in his own mailbox.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });
        }

        @Test
        void shouldRemoveKeywordViewForShareeAndKeepItForOwnerWhenOwnerMovesMessageFromSharedMailboxToPersonalMailbox() throws Exception {
            MailboxId ownerPersonalMailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.forUser(OWNER, "private"), ownerSession)).block();

            // Owner shares the mailbox with the sharee and appends a message with a concerned keyword.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition(),
                ownerSession);
            MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED)));
            MessageId messageId = appendResult.getId().getMessageId();
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).containsExactly(messageId);
            });

            // Owner moves the message to a personal mailbox.
            Flux.from(mailboxManager.moveMessagesReactive(MessageRange.one(appendResult.getId().getUid()), mailboxId, ownerPersonalMailboxId, ownerSession))
                .then()
                .block();

            // Owner should keep the keyword-view entry because the message is still in an owner mailbox,
            // but sharee should lose it because he no longer has access to the message.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).isEmpty();
            });
        }

        @Test
        void shouldKeepKeywordViewWhenOwnerMovesMessageWithinOwnerOnlyMailboxes() throws Exception {
            MailboxId ownerPersonalMailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.forUser(OWNER, "private"), ownerSession)).block();

            MessageManager.AppendResult appendResult = appendMessage(asFlags(List.of(Flags.Flag.FLAGGED)));
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).isEmpty();
            });

            Flux.from(mailboxManager.moveMessagesReactive(MessageRange.one(appendResult.getId().getUid()), mailboxId, ownerPersonalMailboxId, ownerSession))
                .then()
                .block();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, IMPORTANT_FLAGGED)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, IMPORTANT_FLAGGED)).isEmpty();
            });
        }

        @Test
        void shouldNotPopulateConcernedKeywordsForUsersWithoutAccess() throws Exception {
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).isEmpty();
            });
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
        void shouldAddConcernedKeywordViewForOwnerAndShareeWhenOwnerAddsFlagsOnSharedMailbox() throws Exception {
            // Owner shares the mailbox with the sharee
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition(),
                ownerSession);

            // Owner appends a message without any concerned keyword, so neither owner nor sharee sees it in keyword view yet
            MessageManager.AppendResult appendResult = appendMessage(new Flags());
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).isEmpty();
            });

            // Owner adds a concerned keyword on the shared mailbox message
            ownerInbox().setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.ADD, MessageRange.one(uid), ownerSession);

            // Owner and sharee should both see the keyword-view entry for the same message
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldRemoveConcernedKeywordViewForOwnerAndShareeWhenOwnerRemovesFlagsOnSharedMailbox() throws Exception {
            // Owner shares the mailbox with the sharee
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition(),
                ownerSession);

            // Owner appends a message that already has the concerned keyword, so both users see it in keyword view
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });

            // Owner removes the concerned keyword from the shared mailbox message
            ownerInbox().setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.REMOVE, MessageRange.one(uid), ownerSession);

            // Owner and sharee should both lose the keyword-view entry for that messag
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).isEmpty();
            });
        }

        @Test
        void shouldReplaceConcernedKeywordViewForOwnerAndShareeWhenOwnerReplacesFlagsOnSharedMailbox() throws Exception {
            // Owner shares the mailbox with the sharee
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup)
                    .asAddition(),
                ownerSession);

            // Owner appends a message with an old concerned keyword, so both users initially see that keyword in keyword view.
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_OLD_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_OLD_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_OLD_KEYWORD)).containsExactly(messageId);
            });

            // Owner replaces flags with a new concerned keyword
            ownerInbox().setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.one(uid), ownerSession);

            // Owner and sharee should both lose the old keyword entry and gain the new one
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_OLD_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, USER_OLD_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldAddConcernedKeywordViewForOwnerAndShareeWhenShareeAddsFlagsOnSharedMailbox() throws Exception {
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);

            // Owner shares the mailbox with the sharee and allows flag updates.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup, MailboxACL.Right.Write)
                    .asAddition(),
                ownerSession);

            // Owner appends a message without any concerned keyword, so neither owner nor sharee sees it in keyword view yet.
            MessageManager.AppendResult appendResult = appendMessage(new Flags());
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).isEmpty();
            });

            // Sharee adds a concerned keyword on the shared mailbox message.
            mailboxManager.getMailbox(mailboxId, shareeSession)
                .setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.ADD, MessageRange.one(uid), shareeSession);

            // Owner and sharee should both see the keyword-view entry for the same message.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldAddConcernedKeywordViewForTeamMailboxMemberOnFlagsUpdatedEvent() throws Exception {
            TeamMailbox teamMailbox = createTeamMailbox(TEAM_MAILBOX_NAME);
            addMemberToTeamMailbox(teamMailbox, SHAREE);
            addMemberToTeamMailbox(teamMailbox, SECOND_SHAREE);
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);

            MessageManager.AppendResult appendResult = appendMessageToTeamInbox(teamMailbox, new Flags());
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SECOND_SHAREE, USER_NEW_KEYWORD)).isEmpty();
            });

            teamInbox(teamMailbox, shareeSession).setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.ADD, MessageRange.one(uid), shareeSession);
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SECOND_SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });
        }

        @Test
        void shouldRemoveConcernedKeywordViewForOwnerAndShareeWhenShareeRemovesFlagsOnSharedMailbox() throws Exception {
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);

            // Owner shares the mailbox with the sharee and allows flag updates.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup, MailboxACL.Right.Write)
                    .asAddition(),
                ownerSession);

            // Owner appends a message that already has the concerned keyword, so both users see it in keyword view.
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
            });

            // Sharee removes the concerned keyword from the shared mailbox message.
            mailboxManager.getMailbox(mailboxId, shareeSession)
                .setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.REMOVE, MessageRange.one(uid), shareeSession);

            // Owner and sharee should both lose the keyword-view entry for that message.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).isEmpty();
            });
        }

        @Test
        void shouldReplaceConcernedKeywordViewForOwnerAndShareeWhenShareeReplacesFlagsOnSharedMailbox() throws Exception {
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);

            // Owner shares the mailbox with the sharee and allows flag updates.
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE)
                    .rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup, MailboxACL.Right.Write, MailboxACL.Right.WriteSeenFlag, MailboxACL.Right.DeleteMessages)
                    .asAddition(),
                ownerSession);

            // Owner appends a message with an old concerned keyword, so both users initially see that keyword in keyword view.
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_OLD_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            MessageUid uid = appendResult.getId().getUid();

            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_OLD_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_OLD_KEYWORD)).containsExactly(messageId);
            });

            // Sharee replaces flags with a new concerned keyword on the shared mailbox message.
            mailboxManager.getMailbox(mailboxId, shareeSession)
                .setFlags(asFlags(USER_NEW_KEYWORD), MessageManager.FlagsUpdateMode.REPLACE, MessageRange.one(uid), shareeSession);

            // Owner and sharee should both lose the old keyword entry and gain the new one.
            CALMLY_AWAIT.untilAsserted(() -> {
                assertThat(messageIdsByKeywordView(OWNER, USER_OLD_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(SHAREE, USER_OLD_KEYWORD)).isEmpty();
                assertThat(messageIdsByKeywordView(OWNER, USER_NEW_KEYWORD)).containsExactly(messageId);
                assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD)).containsExactly(messageId);
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
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asAddition(), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));
        }

        @Test
        void shouldDeleteKeywordViewForUsersWhoLoseReadRightOnAMailbox() throws Exception {
            // GIVEN a message with a user keyword and a sharee with read right on the mailbox
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asAddition(), ownerSession);
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));

            // WHEN we remove read right for the sharee
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asRemoval(), ownerSession);

            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .isEmpty());
        }

        @Test
        void shouldKeepKeywordViewWhenUserLosesReadRightButStillCanAccessMessageFromAnotherMailbox() throws Exception {
            // GIVEN Alice appends a keyword message in her mailbox
            MessageManager.AppendResult appendResult = appendMessage(asFlags(USER_NEW_KEYWORD));
            MessageId messageId = appendResult.getId().getMessageId();
            MailboxSession shareeSession = mailboxManager.createSystemSession(SHAREE);
            MailboxId shareeMailboxId = Mono.from(mailboxManager.createMailboxReactive(MailboxPath.inbox(SHAREE), shareeSession)).block();

            // AND Alice shares the mailbox with Bob so Bob gets keyword-view entries for that shared message
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asAddition(), ownerSession);
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));

            // AND Bob copies that same message into his own mailbox, keeping the same messageId
            Flux.from(mailboxManager.copyMessagesReactive(MessageRange.one(appendResult.getId().getUid()), mailboxId, shareeMailboxId, shareeSession))
                .then()
                .block();

            // WHEN Alice revokes Bob's access to the shared mailbox
            resources.getStoreRightManager().applyRightsCommand(mailboxId,
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asRemoval(), ownerSession);

            // THEN Bob should still keep the keyword-view entry because he can still access the copied message from his own mailbox
            CALMLY_AWAIT.untilAsserted(() -> assertThat(messageIdsByKeywordView(SHAREE, USER_NEW_KEYWORD))
                .containsExactly(messageId));
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
                MailboxACL.command().forUser(SHAREE).rights(MailboxACL.Right.Read, MailboxACL.Right.Lookup).asAddition(), ownerSession);
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

    private TeamMailbox createTeamMailbox(String teamMailboxName) {
        TeamMailbox teamMailbox = OptionConverters.toJava(TeamMailbox.fromJava(DOMAIN, teamMailboxName)).orElseThrow();
        Mono.from(teamMailboxRepository.createTeamMailbox(teamMailbox)).block();
        return teamMailbox;
    }

    private void addMemberToTeamMailbox(TeamMailbox teamMailbox, Username username) {
        Mono.from(teamMailboxRepository.addMember(teamMailbox, TeamMailboxMember.asMember(username))).block();
    }

    private MessageManager.AppendResult appendMessageToTeamInbox(TeamMailbox teamMailbox, Flags flags) throws MailboxException {
        return teamInbox(teamMailbox).appendMessage(MessageManager.AppendCommand.builder()
            .withInternalDate(Date.from(INTERNAL_DATE))
            .withFlags(flags)
            .notRecent()
            .build("Subject: team test\r\n\r\nbody"), teamOwnerSession(teamMailbox));
    }

    private MessageManager ownerInbox() throws MailboxException {
        return mailboxManager.getMailbox(mailboxId, ownerSession);
    }

    private MessageManager teamInbox(TeamMailbox teamMailbox) throws MailboxException {
        return mailboxManager.getMailbox(teamMailbox.inboxPath(), teamOwnerSession(teamMailbox));
    }

    private MessageManager teamInbox(TeamMailbox teamMailbox, MailboxSession mailboxSession) throws MailboxException {
        return mailboxManager.getMailbox(teamMailbox.inboxPath(), mailboxSession);
    }

    private MailboxSession teamOwnerSession(TeamMailbox teamMailbox) {
        return mailboxManager.createSystemSession(teamMailbox.owner());
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
