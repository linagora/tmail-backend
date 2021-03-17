package com.linagora.openpaas.james.jmap.json

import com.linagora.openpaas.james.jmap.model.{Action, AppendIn, Comparator, Condition, Field, Filter, FilterGetIds, FilterGetNotFound, FilterGetRequest, FilterGetResponse, FilterSetError, FilterSetRequest, FilterSetResponse, FilterSetUpdateResponse, Rule, RuleWithId, Update}
import eu.timepit.refined.api.{RefType, Validate}
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{AccountId, State}
import org.apache.james.jmap.mail.Name
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

import javax.inject.Inject

case class FilterSerializer @Inject()(mailboxIdFactory: MailboxId.Factory) {

  implicit def writeRefined[T, P, F[_, _]](
                                            implicit writesT: Writes[T],
                                            reftype: RefType[F]
                                          ): Writes[F[T, P]] = Writes(value => writesT.writes(reftype.unwrap(value)))
  implicit def readRefined[T, P, F[_, _]](
                                           implicit readsT: Reads[T],
                                           reftype: RefType[F],
                                           validate: Validate[T, P]
                                         ): Reads[F[T, P]] =
    Reads(jsValue =>
      readsT.reads(jsValue).flatMap { valueT =>
        reftype.refine[P](valueT) match {
          case Right(valueP) => JsSuccess(valueP)
          case Left(error)   => JsError(error)
        }
      })

  implicit val filterGetIds: Reads[FilterGetIds] = Json.valueReads[FilterGetIds]
  implicit val accountId: Reads[AccountId] = Json.valueReads[AccountId]
  implicit val filterGetRequestReads: Reads[FilterGetRequest] = Json.reads[FilterGetRequest]

  implicit val mailboxIdWrites: Writes[MailboxId] = mailboxId => JsString(mailboxId.serialize)
  implicit val appendIn: Writes[AppendIn] = Json.writes[AppendIn]
  implicit val actionWrites: Writes[Action] = Json.writes[Action]
  implicit val comparatorWrites: Writes[Comparator] = Json.valueWrites[Comparator]
  implicit val fieldWrites: Writes[Field] = Json.valueWrites[Field]
  implicit val conditionWrites: Writes[Condition] = Json.writes[Condition]
  implicit val nameWrites: Writes[Name] = Json.valueWrites[Name]
  implicit val ruleWrites: Writes[Rule] = Json.writes[Rule]
  implicit val filterWrites: Writes[Filter] = Json.writes[Filter]
  implicit val notFoundWrites: Writes[FilterGetNotFound] = Json.valueWrites[FilterGetNotFound]
  implicit val filterGetResponseAccountId: Writes[AccountId] = Json.valueWrites[AccountId]
  implicit val filterGetResponseWrites: Writes[FilterGetResponse] = Json.writes[FilterGetResponse]

  implicit val nameReads: Reads[Name] = Json.valueReads[Name]
  implicit val fieldReads: Reads[Field] = Json.valueReads[Field]
  implicit val comparatorReads: Reads[Comparator] = Json.valueReads[Comparator]
  implicit val conditionReads: Reads[Condition] = Json.reads[Condition]
  implicit val mailboxIdReads: Reads[MailboxId] = {
    case JsString(serializedMailboxId) => JsSuccess(mailboxIdFactory.fromString(serializedMailboxId))
    case _ => JsError()
  }
  implicit val appendInReads: Reads[AppendIn] = Json.reads[AppendIn]
  implicit val actionReads: Reads[Action] = Json.reads[Action]
  implicit val ruleWithIdReads: Reads[RuleWithId] = Json.reads[RuleWithId]
  implicit val updateReads: Reads[Update] = Json.valueReads[Update]
  implicit val filterSetRequestReads: Reads[FilterSetRequest] = Json.reads[FilterSetRequest]

  def serialize(response: FilterGetResponse): JsValue = Json.toJson(response)

  implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]
  implicit val filterSetErrorWrites: Writes[FilterSetError] = Json.writes[FilterSetError]
  implicit val filterSetUpdateResponseWrites: Writes[FilterSetUpdateResponse] = Json.valueWrites[FilterSetUpdateResponse]
  implicit val stateWrites: Writes[State] = Json.valueWrites[State]
  implicit val filterSetResponseWrites: Writes[FilterSetResponse] = Json.writes[FilterSetResponse]

  def serializeFilterSetResponse(response: FilterSetResponse): JsValue = Json.toJson(response)

  def deserializeFilterGetRequest(input: JsValue): JsResult[FilterGetRequest] = Json.fromJson[FilterGetRequest](input)

  def deserializeFilterSetRequest(input: JsValue): JsResult[FilterSetRequest] = Json.fromJson[FilterSetRequest](input)
}
