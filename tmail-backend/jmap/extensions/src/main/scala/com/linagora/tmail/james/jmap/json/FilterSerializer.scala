package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{Action, AppendIn, Comparator, Condition, Field, Filter, FilterGetIds, FilterGetNotFound, FilterGetRequest, FilterGetResponse, FilterSetError, FilterSetRequest, FilterSetResponse, FilterSetUpdateResponse, FilterState, MarkAsImportant, MarkAsSeen, Reject, Rule, RuleWithId, Update, WithKeywords}
import javax.inject.Inject
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

  implicit val conditionReads: Reads[Condition] = Json.reads[Condition]
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
  implicit val actionFormat: Format[Action] = Json.format[Action]
  implicit val ruleWrites: Writes[Rule] = Json.writes[Rule]
  implicit val filterWrites: Writes[Filter] = Json.writes[Filter]
  implicit val notFoundWrites: Writes[FilterGetNotFound] = Json.valueWrites[FilterGetNotFound]
  implicit val filterStateWrites: Writes[FilterState] = state => JsString(state.serialize)
  implicit val filterGetResponseWrites: Writes[FilterGetResponse] = Json.writes[FilterGetResponse]
  implicit val ruleWithIdReads: Reads[RuleWithId] = Json.reads[RuleWithId]
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
