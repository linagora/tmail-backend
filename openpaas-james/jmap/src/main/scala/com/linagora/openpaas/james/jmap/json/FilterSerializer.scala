package com.linagora.openpaas.james.jmap.json

import com.linagora.openpaas.james.jmap.model.{Action, AppendIn, Comparator, Condition, Field, Filter, FilterGetIds, FilterGetNotFound, FilterGetRequest, FilterGetResponse, Rule}
import eu.timepit.refined.api.{RefType, Validate}
import org.apache.james.jmap.core.AccountId
import org.apache.james.jmap.mail.Name
import org.apache.james.mailbox.model.MailboxId
import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

object FilterSerializer {

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

  def serialize(response: FilterGetResponse): JsValue = Json.toJson(response)

  def deserializeFilterGetRequest(input: JsValue): JsResult[FilterGetRequest] = Json.fromJson[FilterGetRequest](input)
}
