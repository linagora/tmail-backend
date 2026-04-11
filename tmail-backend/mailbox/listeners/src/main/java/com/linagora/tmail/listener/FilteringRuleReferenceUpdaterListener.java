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

import java.util.List;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.core.Username;
import org.apache.james.events.Event;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.jmap.api.filtering.FilteringManagement;
import org.apache.james.jmap.api.filtering.Rule;
import org.apache.james.jmap.api.filtering.Rules;
import org.apache.james.mailbox.events.MailboxEvents;
import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

/**
 * Listens to mailbox deletion events and removes any filtering rules that
 * reference the deleted mailbox. If removing the mailbox reference from a
 * rule's appendInMailboxes action leaves the list empty, the entire rule is
 * dropped.
 */
public class FilteringRuleReferenceUpdaterListener implements EventListener.ReactiveGroupEventListener {

    public static class FilteringRuleReferenceUpdaterListenerGroup extends Group {
    }

    private static final FilteringRuleReferenceUpdaterListenerGroup GROUP =
        new FilteringRuleReferenceUpdaterListenerGroup();

    private final FilteringManagement filteringManagement;

    @Inject
    public FilteringRuleReferenceUpdaterListener(FilteringManagement filteringManagement) {
        this.filteringManagement = filteringManagement;
    }

    @Override
    public Group getDefaultGroup() {
        return GROUP;
    }

    @Override
    public boolean isHandling(Event event) {
        return event instanceof MailboxEvents.MailboxDeletion;
    }

    @Override
    public Publisher<Void> reactiveEvent(Event event) {
        MailboxEvents.MailboxDeletion deletion = (MailboxEvents.MailboxDeletion) event;
        String deletedMailboxId = deletion.getMailboxId().serialize();

        return Mono.from(filteringManagement.listRulesForUser(deletion.getUsername()))
            .flatMap(rules -> updateRules(rules, deletedMailboxId, deletion.getUsername()));
    }

    private Mono<Void> updateRules(Rules rules, String deletedMailboxId, Username username) {
        List<Rule> updatedRules = rules.getRules().stream()
            .map(rule -> removeDeletedMailbox(rule, deletedMailboxId))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(ImmutableList.toImmutableList());

        boolean changed = updatedRules.size() != rules.getRules().size()
            || !updatedRules.equals(rules.getRules());

        if (!changed) {
            return Mono.empty();
        }

        return Mono.from(filteringManagement.defineRulesForUser(username, updatedRules, Optional.empty()))
            .then();
    }

    /**
     * Returns the rule with the deleted mailbox removed from its appendInMailboxes action,
     * or empty if the rule's only mailbox target was the deleted mailbox.
     */
    private Optional<Rule> removeDeletedMailbox(Rule rule, String deletedMailboxId) {
        ImmutableList<String> originalMailboxIds =
            rule.getAction().getAppendInMailboxes().getMailboxIds();

        if (!originalMailboxIds.contains(deletedMailboxId)) {
            return Optional.of(rule);
        }

        ImmutableList<String> filteredMailboxIds = originalMailboxIds.stream()
            .filter(id -> !id.equals(deletedMailboxId))
            .collect(ImmutableList.toImmutableList());

        if (filteredMailboxIds.isEmpty()) {
            return Optional.empty();
        }

        Rule updatedRule = Rule.builder()
            .id(rule.getId())
            .name(rule.getName())
            .conditionGroup(rule.getConditionGroup())
            .action(Rule.Action.builder()
                .setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds(filteredMailboxIds))
                .setMarkAsSeen(rule.getAction().isMarkAsSeen())
                .setMarkAsImportant(rule.getAction().isMarkAsImportant())
                .setReject(rule.getAction().isReject())
                .setWithKeywords(ImmutableList.copyOf(rule.getAction().getWithKeywords()))
                .setForward(rule.getAction().getForward())
                .setMoveTo(rule.getAction().getMoveTo())
                .build())
            .build();

        return Optional.of(updatedRule);
    }
}
