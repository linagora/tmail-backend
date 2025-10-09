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

package com.linagora.tmail.james.jmap.model

import java.util.Optional

import com.google.common.collect.ImmutableList
import org.apache.james.core.MailAddress
import org.apache.james.jmap.api.filtering.Rule
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType, invalidArgumentValue, serverFailValue, stateMismatchValue}
import org.apache.james.jmap.mail.Name
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsArray, JsObject}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._


case class FilterSetRequest(accountId: AccountId,
                            ifInState: Option[FilterState],
                            update: Option[Map[String, Update]],
                            create: Option[Map[String, JsArray]],
                            destroy: Option[Seq[String]]) extends WithAccountId {
  def parseUpdate(): Map[String, Either[IllegalArgumentException, Update]] =
  update.getOrElse(Map())
    .map({
      case (id, update) if id.equals("singleton") => (id, Right(update))
      case (id, _) => (id, Left(new IllegalArgumentException(s"id $id must be singleton")))
    })

}

case class Update(rules: List[RuleWithId])

case class RuleWithId(id: Id, name: Name, conditionGroup: ConditionGroup, action: Action)

case class SerializedRule(id: Id, name: Name, conditionGroup: Option[ConditionGroup], condition: Option[Condition], action: Action)

case class FilterSetResponse(accountId: AccountId,
                             oldState: Option[FilterState],
                             newState: FilterState,
                             updated: Option[Map[String, FilterSetUpdateResponse]],
                             notUpdated: Option[Map[String, FilterSetError]],
                             notCreated: Option[Map[String, FilterSetError]],
                             notDestroyed: Option[Map[String, FilterSetError]])

case class FilterSetUpdateResponse(value: JsObject)

object FilterSetError {
  def invalidArgument(description: Option[SetErrorDescription]): FilterSetError = FilterSetError(invalidArgumentValue, description)
  def serverFail(description: Option[SetErrorDescription]): FilterSetError = FilterSetError(serverFailValue, description)
  def stateMismatch(description: Option[SetErrorDescription]): FilterSetError = FilterSetError(stateMismatchValue, description)
}

object RuleWithId {
  def toJava(rules: List[RuleWithId]): List[Rule] =
    rules.map(ruleWithId => Rule.builder()
        .id(Rule.Id.of(ruleWithId.id.value))
        .name(ruleWithId.name.value)
        .conditionGroup(toConditionGroup(ruleWithId))
        .action(asAction(ruleWithId))
        .build())

  private def toConditionGroup(ruleWithId: RuleWithId): Rule.ConditionGroup =
    Rule.ConditionGroup.of(Rule.ConditionCombiner.valueOf(ruleWithId.conditionGroup.conditionCombiner.toString),
      ruleWithId.conditionGroup.conditions.map(convertScalaConditionToJavaCondition(_)).asJava)

  private def convertScalaConditionToJavaCondition(condition: Condition): Rule.Condition = Rule.Condition.of(Rule.Condition.Field.of(condition.field.string),
    Rule.Condition.Comparator.of(condition.comparator.string),
    condition.value)

  private def asAction(ruleWithId: RuleWithId): Rule.Action =
    Rule.Action.builder.setAppendInMailboxes(Rule.Action.AppendInMailboxes.withMailboxIds(AppendIn.convertListMailboxIdToListString(ruleWithId.action.appendIn.mailboxIds).asJava))
      .setMarkAsSeen(ruleWithId.action.markAsSeen.map(_.value).getOrElse(false))
      .setMarkAsImportant(ruleWithId.action.markAsImportant.map(_.value).getOrElse(false))
      .setReject(ruleWithId.action.reject.map(_.value).getOrElse(false))
      .setWithKeywords(ruleWithId.action.withKeywords.map(keywords => ImmutableList.copyOf(keywords.keywords.map(k => k.flagName).toList.asJavaCollection)).getOrElse(ImmutableList.of()))
      .setForward(convertScalaForwardToJavaForward(ruleWithId.action.forwardTo))
      .setMoveTo(ruleWithId.action.moveTo.map(moveTo => new Rule.Action.MoveTo(moveTo.mailboxName.value)).toJava)
      .build()

  private def convertScalaForwardToJavaForward(forwardOption: Option[FilterForward]): Optional[Rule.Action.Forward] =
    forwardOption.map(forward => Optional.of(Rule.Action.Forward.of(
      forward.addresses.map(address => new MailAddress(address.string)).asJava,
      forward.keepACopy.value)))
      .getOrElse(Optional.empty())
}

case class FilterSetError(`type`: SetErrorType, description: Option[SetErrorDescription])
