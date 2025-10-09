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

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.ConditionCombiner.ConditionCombiner
import com.linagora.tmail.james.jmap.model.{Action, AppendIn, Comparator, Condition, ConditionCombiner, ConditionGroup, Field, Filter, FilterForward, FilterGetIds, FilterGetNotFound, FilterGetRequest, FilterGetResponse, FilterSetError, FilterSetRequest, FilterSetResponse, FilterSetUpdateResponse, FilterState, KeepACopy, MailAddress, MarkAsImportant, MarkAsSeen, MoveTo, Reject, Rule, RuleWithId, SerializedRule, Update, WithKeywords}
import jakarta.inject.Inject
import org.apache.james.jmap.mail.{Keyword, Name}
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

case class FilterSerializer @Inject()(mailboxIdFactory: MailboxId.Factory) {

  implicit val comparatorFormat: Format[Comparator] = Json.valueFormat[Comparator]
  implicit val fieldFormat: Format[Field] = Json.valueFormat[Field]
  implicit val nameFormat: Format[Name] = Json.valueFormat[Name]

  implicit val filterGetIds: Reads[FilterGetIds] = Json.valueReads[FilterGetIds]
  implicit val filterGetRequestReads: Reads[FilterGetRequest] = Json.reads[FilterGetRequest]

  implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)
  implicit val appendIn: Writes[AppendIn] = Json.writes[AppendIn]
  implicit val conditionWrites: Writes[Condition] = Json.writes[Condition]
  implicit val conditionGroupWrites: Writes[ConditionGroup] = Json.writes[ConditionGroup]

  implicit val conditionReads: Reads[Condition] = Json.reads[Condition]
  implicit val conditionCombinerReads: Reads[ConditionCombiner] = Reads.enumNameReads(ConditionCombiner)
  implicit val conditionGroupReads: Reads[ConditionGroup] = Json.reads[ConditionGroup]
  implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))
    case _ => JsError()
  }
  implicit val appendInReads: Reads[AppendIn] = Json.reads[AppendIn]
  private implicit val keywordReads: Reads[Keyword] = {
    case jsString: JsString => Keyword.parse(jsString.value)
      .fold(JsError(_),
        JsSuccess(_))
    case _ => JsError("Expecting a string as a keyword")
  }
  implicit val keywordWrites: Writes[Keyword] = Json.valueWrites[Keyword]
  implicit val markAsSeenFormat: Format[MarkAsSeen] = Json.valueFormat[MarkAsSeen]
  implicit val markAsImportantFormat: Format[MarkAsImportant] = Json.valueFormat[MarkAsImportant]
  implicit val rejectFormat: Format[Reject] = Json.valueFormat[Reject]
  implicit val withKeywordsFormat: Format[WithKeywords] = Json.valueFormat[WithKeywords]
  implicit val mailAddressFormat: Format[MailAddress] = Json.valueFormat[MailAddress]
  implicit val keepACopyFormat: Format[KeepACopy] = Json.valueFormat[KeepACopy]
  implicit val forwardToFormat: Format[FilterForward] = Json.format[FilterForward]
  implicit val moveToFormat: Format[MoveTo] = Json.format[MoveTo]
  implicit val actionFormat: Format[Action] = Json.format[Action]
  implicit val ruleWrites: Writes[Rule] = Json.writes[Rule]
  implicit val filterWrites: Writes[Filter] = Json.writes[Filter]
  implicit val notFoundWrites: Writes[FilterGetNotFound] = Json.valueWrites[FilterGetNotFound]
  implicit val filterStateWrites: Writes[FilterState] = state => JsString(state.serialize)
  implicit val filterGetResponseWrites: Writes[FilterGetResponse] = Json.writes[FilterGetResponse]
  implicit val serializedRuleReads: Reads[SerializedRule] = Json.reads[SerializedRule]
  implicit val ruleWithIdReads: Reads[RuleWithId] = jsValue => serializedRuleReads.reads(jsValue)
    .flatMap {
      case s if s.condition.isEmpty && s.conditionGroup.isEmpty => JsError("condition or conditionGroup needs to be specified")
      case s if s.condition.isDefined && s.conditionGroup.isDefined => JsError("condition and conditionGroup cannot be specified at the same time")
      case s =>
        val conditionGroup: ConditionGroup = s.conditionGroup.getOrElse(ConditionGroup(ConditionCombiner.AND, s.condition.toList))
        JsSuccess(RuleWithId(s.id, s.name, conditionGroup, s.action))
    }
  implicit val updateReads: Reads[Update] = Json.valueReads[Update]
  implicit val filterStateReads: Reads[FilterState] = {
    case JsString(string) => FilterState.parse(string).fold(
      error => JsError(error.getMessage),
      filterState => JsSuccess(filterState))
    case _ => JsError()
  }
  implicit val filterSetRequestReads: Reads[FilterSetRequest] = Json.reads[FilterSetRequest]

  implicit val filterSetErrorWrites: Writes[FilterSetError] = Json.writes[FilterSetError]
  implicit val filterSetUpdateResponseWrites: Writes[FilterSetUpdateResponse] = Json.valueWrites[FilterSetUpdateResponse]
  implicit val filterSetResponseWrites: Writes[FilterSetResponse] = Json.writes[FilterSetResponse]

  def serializeFilterGetResponse(response: FilterGetResponse): JsValue = Json.toJson(response)

  def serializeFilterSetResponse(response: FilterSetResponse): JsValue = Json.toJson(response)

  def deserializeFilterGetRequest(input: JsValue): JsResult[FilterGetRequest] = Json.fromJson[FilterGetRequest](input)

  def deserializeFilterSetRequest(input: JsValue): JsResult[FilterSetRequest] = Json.fromJson[FilterSetRequest](input)
}
