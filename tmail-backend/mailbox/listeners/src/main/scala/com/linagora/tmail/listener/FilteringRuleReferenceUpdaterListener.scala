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

package com.linagora.tmail.listener

import java.util.Optional

import com.google.common.collect.ImmutableList
import com.linagora.tmail.james.jmap.model.{FilterState, FilterTypeName}
import jakarta.inject.{Inject, Named}
import org.apache.james.core.Username
import org.apache.james.events.Event.EventId
import org.apache.james.events.{Event, EventBus, EventListener, Group}
import org.apache.james.jmap.InjectionKeys
import org.apache.james.jmap.api.filtering.{FilteringManagement, Rule, Rules, Version}
import org.apache.james.jmap.change.{AccountIdRegistrationKey, StateChangeEvent}
import org.apache.james.mailbox.events.MailboxEvents
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import scala.jdk.CollectionConverters._

object FilteringRuleReferenceUpdaterListener {
  class FilteringRuleReferenceUpdaterListenerGroup extends Group {}

  private val GROUP: FilteringRuleReferenceUpdaterListenerGroup =
    new FilteringRuleReferenceUpdaterListenerGroup()
}

/**
 * Listens to mailbox deletion events and removes any filtering rules that
 * reference the deleted mailbox. If removing the mailbox reference from a
 * rule's appendInMailboxes action leaves the list empty, the rule is dropped
 * only when no other actions (markAsSeen, markAsImportant, reject, withKeywords,
 * forward, moveTo) remain; otherwise the rule is preserved with an empty
 * appendInMailboxes list.
 *
 * After updating the rules, a StateChangeEvent is dispatched on the JMAP event
 * bus so that connected clients can sync their Filter state.
 */
class FilteringRuleReferenceUpdaterListener @Inject()(
    filteringManagement: FilteringManagement,
    @Named(InjectionKeys.JMAP) eventBus: EventBus
) extends EventListener.ReactiveGroupEventListener {

  override def getDefaultGroup: Group = FilteringRuleReferenceUpdaterListener.GROUP

  override def isHandling(event: Event): Boolean =
    event.isInstanceOf[MailboxEvents.MailboxDeletion]

  override def reactiveEvent(event: Event): Publisher[Void] = {
    val deletion = event.asInstanceOf[MailboxEvents.MailboxDeletion]
    val deletedMailboxId = deletion.getMailboxId.serialize()

    Mono.from(filteringManagement.listRulesForUser(deletion.getUsername))
      .flatMap[Void](rules => updateRules(rules, deletedMailboxId, deletion.getUsername))
  }

  private def updateRules(rules: Rules, deletedMailboxId: String, username: Username): Mono[Void] = {
    val updatedRules = rules.getRules.asScala
      .flatMap(rule => removeDeletedMailbox(rule, deletedMailboxId))
      .toList

    val changed = updatedRules.size != rules.getRules.size ||
      updatedRules != rules.getRules.asScala.toList

    if (!changed) {
      Mono.empty()
    } else {
      Mono.from(filteringManagement.defineRulesForUser(username, updatedRules.asJava, Optional.empty[Version]()))
        .flatMap[Void](newVersion => dispatchStateChangeEvent(username, newVersion.asInteger()))
    }
  }

  private def dispatchStateChangeEvent(username: Username, version: Int): Mono[Void] = {
    val stateChangeEvent = StateChangeEvent(
      eventId = EventId.random(),
      username = username,
      map = Map(FilterTypeName -> FilterState(version))
    )
    Mono.from(eventBus.dispatch(stateChangeEvent, AccountIdRegistrationKey.of(username)))
  }

  private def hasNonAppendActions(rule: Rule): Boolean = {
    val action = rule.getAction
    action.isMarkAsSeen || action.isMarkAsImportant || action.isReject ||
      !action.getWithKeywords.isEmpty || action.getForward.isPresent || action.getMoveTo.isPresent
  }

  /**
   * Returns the rule with the deleted mailbox removed from its appendInMailboxes action.
   * The rule is dropped only when the appendInMailboxes list becomes empty AND no other
   * actions (markAsSeen, markAsImportant, reject, withKeywords, forward, moveTo) remain.
   */
  private def removeDeletedMailbox(rule: Rule, deletedMailboxId: String): Option[Rule] = {
    val originalMailboxIds = rule.getAction.getAppendInMailboxes.getMailboxIds.asScala.toList

    if (!originalMailboxIds.contains(deletedMailboxId)) {
      return Some(rule)
    }

    val filteredMailboxIds = originalMailboxIds.filterNot(_ == deletedMailboxId)

    if (filteredMailboxIds.isEmpty && !hasNonAppendActions(rule)) {
      return None
    }

    Some(Rule.builder()
      .id(rule.getId)
      .name(rule.getName)
      .conditionGroup(rule.getConditionGroup)
      .action(Rule.Action.builder()
        .setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds(filteredMailboxIds.asJava))
        .setMarkAsSeen(rule.getAction.isMarkAsSeen)
        .setMarkAsImportant(rule.getAction.isMarkAsImportant)
        .setReject(rule.getAction.isReject)
        .setWithKeywords(ImmutableList.copyOf(rule.getAction.getWithKeywords))
        .setForward(rule.getAction.getForward)
        .setMoveTo(rule.getAction.getMoveTo)
        .build())
      .build())
  }
}
