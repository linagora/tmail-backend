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

package com.linagora.tmail.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.eventsourcing.eventstore.memory.InMemoryEventStore;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.impl.EventSourcingFilteringManagement;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class FilteringRuleReferenceUpdaterListenerTest {

    private static final Username BOB = Username.of("bob@domain.tld");
    private static final Rule.Id RULE_ID_1 = Rule.Id.of("rule-1");
    private static final Rule.Id RULE_ID_2 = Rule.Id.of("rule-2");

    private MailboxManager mailboxManager;
    private FilteringManagement filteringManagement;

    @BeforeEach
    void setUp() {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        filteringManagement = new EventSourcingFilteringManagement(new InMemoryEventStore());

        FilteringRuleReferenceUpdaterListener listener =
            new FilteringRuleReferenceUpdaterListener(filteringManagement);
        resources.getEventBus().register(listener);
    }

    @Test
    void shouldRemoveRuleWhenItsOnlyTargetMailboxIsDeleted() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxId folderId = mailboxManager.createMailbox(folderPath, session).orElseThrow();

        Rule rule = Rule.builder()
            .id(RULE_ID_1)
            .name("Move to FolderB")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(folderId.serialize())))
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(rule), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).isEmpty();
    }

    @Test
    void shouldRemoveOnlyTheDeletedMailboxFromRuleWithMultipleTargets() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderBPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxPath folderCPath = MailboxPath.forUser(BOB, "FolderC");
        MailboxId folderBId = mailboxManager.createMailbox(folderBPath, session).orElseThrow();
        MailboxId folderCId = mailboxManager.createMailbox(folderCPath, session).orElseThrow();

        Rule rule = Rule.builder()
            .id(RULE_ID_1)
            .name("Move to FolderB or FolderC")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(
                List.of(folderBId.serialize(), folderCId.serialize()))))
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(rule), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderBPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getAction().getAppendInMailboxes().getMailboxIds())
            .containsExactly(folderCId.serialize());
    }

    @Test
    void shouldNotAffectRulesNotReferencingDeletedMailbox() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderBPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxPath folderCPath = MailboxPath.forUser(BOB, "FolderC");
        MailboxId folderBId = mailboxManager.createMailbox(folderBPath, session).orElseThrow();
        MailboxId folderCId = mailboxManager.createMailbox(folderCPath, session).orElseThrow();

        Rule rule = Rule.builder()
            .id(RULE_ID_1)
            .name("Move to FolderC")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(folderCId.serialize())))
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(rule), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderBPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).containsExactly(rule);
    }

    @Test
    void shouldRemoveOnlyAffectedRulesWhenMultipleRulesExist() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderBPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxPath folderCPath = MailboxPath.forUser(BOB, "FolderC");
        MailboxId folderBId = mailboxManager.createMailbox(folderBPath, session).orElseThrow();
        MailboxId folderCId = mailboxManager.createMailbox(folderCPath, session).orElseThrow();

        Rule ruleTargetingB = Rule.builder()
            .id(RULE_ID_1)
            .name("Move to FolderB")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(folderBId.serialize())))
            .build();
        Rule ruleTargetingC = Rule.builder()
            .id(RULE_ID_2)
            .name("Move to FolderC")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.FROM, Rule.Condition.Comparator.CONTAINS, "bob"))
            .action(Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(folderCId.serialize())))
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(ruleTargetingB, ruleTargetingC), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderBPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).containsExactly(ruleTargetingC);
    }

    @Test
    void shouldDropRuleWhenLastMailboxIsDeletedAndNoOtherActionsPresent() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderBPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxId folderBId = mailboxManager.createMailbox(folderBPath, session).orElseThrow();

        Rule rule = Rule.builder()
            .id(RULE_ID_1)
            .name("Move to FolderB only")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.builder()
                .setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds(folderBId.serialize()))
                .setMarkAsSeen(false)
                .setMarkAsImportant(false)
                .setReject(false)
                .setWithKeywords(List.of())
                .build())
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(rule), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderBPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).isEmpty();
    }

    @Test
    void shouldPreserveRuleWithEmptyAppendInMailboxesWhenOtherActionsRemain() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderBPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxId folderBId = mailboxManager.createMailbox(folderBPath, session).orElseThrow();

        Rule rule = Rule.builder()
            .id(RULE_ID_1)
            .name("Mark as seen and move to FolderB")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.builder()
                .setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds(folderBId.serialize()))
                .setMarkAsSeen(true)
                .build())
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(rule), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderBPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).hasSize(1);
        Rule preserved = remaining.get(0);
        assertThat(preserved.getAction().getAppendInMailboxes().getMailboxIds()).isEmpty();
        assertThat(preserved.getAction().isMarkAsSeen()).isTrue();
    }

    @Test
    void shouldPreserveOtherActionFieldsWhenRemovingMailboxFromRule() throws Exception {
        MailboxSession session = mailboxManager.createSystemSession(BOB);
        MailboxPath folderBPath = MailboxPath.forUser(BOB, "FolderB");
        MailboxPath folderCPath = MailboxPath.forUser(BOB, "FolderC");
        MailboxId folderBId = mailboxManager.createMailbox(folderBPath, session).orElseThrow();
        MailboxId folderCId = mailboxManager.createMailbox(folderCPath, session).orElseThrow();

        Rule rule = Rule.builder()
            .id(RULE_ID_1)
            .name("Move and mark as seen")
            .conditionGroup(Rule.Condition.of(Rule.Condition.FixedField.SUBJECT, Rule.Condition.Comparator.CONTAINS, "hello"))
            .action(Rule.Action.builder()
                .setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds(
                    List.of(folderBId.serialize(), folderCId.serialize())))
                .setMarkAsSeen(true)
                .setMarkAsImportant(true)
                .build())
            .build();
        Mono.from(filteringManagement.defineRulesForUser(BOB, List.of(rule), Optional.empty())).block();

        mailboxManager.deleteMailbox(folderBPath, session);

        List<Rule> remaining = Mono.from(filteringManagement.listRulesForUser(BOB))
            .block()
            .getRules();
        assertThat(remaining).hasSize(1);
        Rule updatedRule = remaining.get(0);
        assertThat(updatedRule.getAction().getAppendInMailboxes().getMailboxIds())
            .containsExactly(folderCId.serialize());
        assertThat(updatedRule.getAction().isMarkAsSeen()).isTrue();
        assertThat(updatedRule.getAction().isMarkAsImportant()).isTrue();
    }
}
