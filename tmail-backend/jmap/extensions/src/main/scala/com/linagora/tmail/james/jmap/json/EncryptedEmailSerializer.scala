package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.encrypted.{EncryptedAttachmentMetadata, EncryptedEmailDetailedView, EncryptedEmailFastView, EncryptedHtml, EncryptedPreview}
import com.linagora.tmail.james.jmap.model.{EncryptedEmailDetailedResponse, EncryptedEmailGetRequest, EncryptedEmailGetResponse}
import eu.timepit.refined
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.UuidState
import org.apache.james.jmap.mail.{EmailIds, EmailNotFound, UnparsedEmailId}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

object EncryptedEmailSerializer {
  private implicit val unparsedMessageIdReads: Reads[UnparsedEmailId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"emailId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedEmailId(id)))
    case _ => JsError("emailId needs to be represented by a JsString")
  }
  private implicit val emailIdsReads: Reads[EmailIds] = Json.valueReads[EmailIds]
  private implicit val emailGetRequestReads: Reads[EncryptedEmailGetRequest] = Json.reads[EncryptedEmailGetRequest]
  private implicit val unparsedMessageIdWrites: Writes[UnparsedEmailId] = Json.valueWrites[UnparsedEmailId]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val emailNotFoundWrites: Writes[EmailNotFound] = Json.valueWrites[EmailNotFound]
  private implicit val messageIdWrites: Writes[MessageId] = id => JsString(id.serialize())
  private implicit val encryptedPreviewWrites: Writes[EncryptedPreview] = Json.valueWrites[EncryptedPreview]
  private implicit val encryptedEmailFastViewWrites: Writes[EncryptedEmailFastView] = Json.writes[EncryptedEmailFastView]
  private implicit val encryptedEmailGetResponseWrites: Writes[EncryptedEmailGetResponse] = Json.writes[EncryptedEmailGetResponse]
  private implicit val encryptedHtmlWrites: Writes[EncryptedHtml] = Json.valueWrites[EncryptedHtml]
  private implicit val encryptedAttachmentMetadataWrites: Writes[EncryptedAttachmentMetadata] = Json.valueWrites[EncryptedAttachmentMetadata]
  private implicit val encryptedEmailDetailedViewWrites: Writes[EncryptedEmailDetailedView] = Json.writes[EncryptedEmailDetailedView]
  private implicit val encryptedEmailDetailedResponseWrites: Writes[EncryptedEmailDetailedResponse] = Json.writes[EncryptedEmailDetailedResponse]

  def deserializeEncryptedEmailGetRequest(input: JsValue): JsResult[EncryptedEmailGetRequest] =
    Json.fromJson[EncryptedEmailGetRequest](input)

  def serializeEncryptedEmailGetResponse(encryptedEmailGetResponse: EncryptedEmailGetResponse) : JsValue =
    Json.toJson(encryptedEmailGetResponse)

  def serializeEncryptedEmailDetailedResponse(encryptedEmailDetailedResponse: EncryptedEmailDetailedResponse): JsValue =
    Json.toJson(encryptedEmailDetailedResponse)
}
