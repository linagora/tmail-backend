package com.linagora.tmail.james.jmap.model

import com.google.common.collect.ImmutableList
import org.apache.james.jmap.api.filtering.Rule
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.{SetErrorDescription, SetErrorType, invalidArgumentValue, serverFailValue, stateMismatchValue}
import org.apache.james.jmap.mail.Name
import org.apache.james.jmap.method.WithAccountId
import play.api.libs.json.{JsArray, JsObject}

import scala.jdk.CollectionConverters._


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

case class RuleWithId(id: Id, name: Name, condition: Condition, action: Action)

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
      .condition(Rule.Condition.of(Rule.Condition.Field.of(ruleWithId.condition.field.string),
        Rule.Condition.Comparator.of(ruleWithId.condition.comparator.string),
        ruleWithId.condition.value))
      .action(asAction(ruleWithId))
      .build())

  private def asAction(ruleWithId: RuleWithId): Rule.Action =
    Rule.Action.of(Rule.Action.AppendInMailboxes.withMailboxIds(AppendIn.convertListMailboxIdToListString(ruleWithId.action.appendIn.mailboxIds).asJava),
      ruleWithId.action.markAsSeen.map(_.value).getOrElse(false),
      ruleWithId.action.markAsImportant.map(_.value).getOrElse(false),
      ruleWithId.action.reject.map(_.value).getOrElse(false),
      ruleWithId.action.withKeywords.map(keywords => ImmutableList.copyOf(keywords.keywords.map(k => k.flagName).toList.asJavaCollection)).getOrElse(ImmutableList.of()))
}

case class FilterSetError(`type`: SetErrorType, description: Option[SetErrorDescription])
