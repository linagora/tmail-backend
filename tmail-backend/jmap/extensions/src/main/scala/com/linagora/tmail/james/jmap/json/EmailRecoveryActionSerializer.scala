package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{EmailRecoveryActionCreationId, EmailRecoveryActionCreationRequest, EmailRecoveryActionCreationResponse, EmailRecoveryActionSetRequest, EmailRecoveryActionSetResponse, EmailRecoveryDeletedAfter, EmailRecoveryDeletedBefore, EmailRecoveryHasAttachment, EmailRecoveryReceivedAfter, EmailRecoveryReceivedBefore, EmailRecoveryRecipient, EmailRecoverySender, EmailRecoverySubject}
import eu.timepit.refined.refineV
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{SetError, UTCDate}
import org.apache.james.jmap.json.{jsObjectReads, mapMarkerReads, mapWrites}
import org.apache.james.task.TaskId
import play.api.libs.json._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.util.{Failure, Success, Try}

object EmailRecoveryActionSerializer {

  private implicit val parseCreationIdFunc: String => JsResult[EmailRecoveryActionCreationId] =
    string => {
      refineV[IdConstraint](string)
        .fold(e => JsError(s"email recovery action id needs to match id constraints: $e"),
          id => JsSuccess(EmailRecoveryActionCreationId(id)))
    }

  private implicit val creationIdReads: Reads[EmailRecoveryActionCreationId] =
    value => value.validate[String].flatMap {
      parseCreationIdFunc
    }
  private implicit val creationIdWrite: Writes[EmailRecoveryActionCreationId] = value => JsString(value.id.value)

  private implicit val mapCreationRequest: Reads[Map[EmailRecoveryActionCreationId, JsObject]] =
    Reads.mapReads[EmailRecoveryActionCreationId, JsObject] {
      parseCreationIdFunc
    }

  private implicit val UTCDateReads: Reads[UTCDate] = {
    case JsString(value) =>
      Try(UTCDate(ZonedDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME))) match {
        case Success(value) => JsSuccess(value)
        case Failure(e) => JsError(e.getMessage)
      }
    case _ => JsError("Expecting js string to represent UTC Date")
  }

  private implicit val emailRecoveryDeletedBeforeReads: Reads[EmailRecoveryDeletedBefore] = Json.valueReads[EmailRecoveryDeletedBefore]
  private implicit val emailRecoveryDeletedAfterReads: Reads[EmailRecoveryDeletedAfter] = Json.valueReads[EmailRecoveryDeletedAfter]
  private implicit val emailRecoveryReceivedBeforeReads: Reads[EmailRecoveryReceivedBefore] = Json.valueReads[EmailRecoveryReceivedBefore]
  private implicit val emailRecoveryReceivedAfterReads: Reads[EmailRecoveryReceivedAfter] = Json.valueReads[EmailRecoveryReceivedAfter]
  private implicit val emailRecoveryHasAttachmentReads: Reads[EmailRecoveryHasAttachment] = Json.valueReads[EmailRecoveryHasAttachment]
  private implicit val emailRecoverySubjectReads: Reads[EmailRecoverySubject] = Json.valueReads[EmailRecoverySubject]
  private implicit val emailRecoverySenderReads: Reads[EmailRecoverySender] = mailAddressReads.map(EmailRecoverySender)
  private implicit val emailRecoveryRecipientReads: Reads[EmailRecoveryRecipient] = Json.valueReads[EmailRecoveryRecipient]
  private implicit val emailRecoveryActionCreationRequestReads: Reads[EmailRecoveryActionCreationRequest] = Json.reads[EmailRecoveryActionCreationRequest]
  private implicit val taskIdWrites: Writes[TaskId] = value => JsString(value.asString())
  private implicit val emailRecoveryActionCreationResponseWrites: Writes[EmailRecoveryActionCreationResponse] = Json.writes[EmailRecoveryActionCreationResponse]
  private implicit val subscriptionMapSetErrorForCreationWrites: Writes[Map[EmailRecoveryActionCreationId, SetError]] =
    mapWrites[EmailRecoveryActionCreationId, SetError](_.serialise, setErrorWrites)

  private implicit def emailRecoveryActionCreationResponseMapWrites(implicit emailRecoveryActionCreationResponseWrites: Writes[EmailRecoveryActionCreationResponse]): Writes[Map[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse]] =
    mapWrites[EmailRecoveryActionCreationId, EmailRecoveryActionCreationResponse](_.id.value, emailRecoveryActionCreationResponseWrites)

  private implicit val emailRecoveryActionSetResponseWrites: OWrites[EmailRecoveryActionSetResponse] = Json.writes[EmailRecoveryActionSetResponse]


  private implicit val emailRecoveryActionSetRequestReads: Reads[EmailRecoveryActionSetRequest] = Json.reads[EmailRecoveryActionSetRequest]


  def deserializeSetRequest(input: JsValue): JsResult[EmailRecoveryActionSetRequest] = Json.fromJson[EmailRecoveryActionSetRequest](input)

  def deserializeSetCreationRequest(input: JsValue): JsResult[EmailRecoveryActionCreationRequest] = Json.fromJson[EmailRecoveryActionCreationRequest](input)

  def serializeSetResponse(response: EmailRecoveryActionSetResponse): JsObject = Json.toJsObject(response)

}
