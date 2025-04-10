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

import com.linagora.tmail.james.jmap.model.{EmailSendCreationId, EmailSendCreationRequestRaw, EmailSendCreationResponse, EmailSendId, EmailSendRequest, EmailSendResponse, EmailSubmissionCreationRequest}
import org.apache.james.jmap.core.{Id, SetError, UuidState}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.{BlobId, EmailSubmissionAddress, EmailSubmissionId, Envelope, ParameterName, ParameterValue, ThreadId}
import org.apache.james.mailbox.model.MessageId
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, JsError, JsNull, JsObject, JsPath, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

object EmailSendSerializer {
  private implicit val parameterNameReads: Reads[ParameterName] = Json.valueReads[ParameterName]
  private implicit val parameterValueReads: Reads[ParameterValue] = Json.valueReads[ParameterValue]
  private implicit val parameterValueOptionReads: Reads[Option[ParameterValue]] = {
    case JsString(value) => JsSuccess(Some(ParameterValue(value)))
    case JsNull => JsSuccess(None)
    case _ => JsError("JsonPath objects are represented by JsonString")
  }

  private implicit val parametersReads: Reads[Map[ParameterName, Option[ParameterValue]]] =
    Reads.mapReads[ParameterName, Option[ParameterValue]](k => JsSuccess(ParameterName(k)))(parameterValueOptionReads)

  private implicit val emailSubmissionAddressReads: Reads[EmailSubmissionAddress] = Json.reads[EmailSubmissionAddress]
  private implicit val envelopeReads: Reads[Envelope] = Json.reads[Envelope]
  private implicit val emailSubmissionCreationRequestReads: Reads[EmailSubmissionCreationRequest] = Json.reads[EmailSubmissionCreationRequest]

  private implicit val emailSendCreationRequestRawReads: Reads[EmailSendCreationRequestRaw] = (
    (JsPath \ "email/create").read[JsObject] and
      (JsPath \ "emailSubmission/set").read[JsObject]
    ) (EmailSendCreationRequestRaw.apply _)

  private implicit val creationIdFormat: Format[EmailSendCreationId] = Json.valueFormat[EmailSendCreationId]

  private implicit val mapEmailSendCreationIdAndObjectReads: Reads[Map[EmailSendCreationId, JsObject]] =
    Reads.mapReads[EmailSendCreationId, JsObject] {
      s => Id.validate(s).fold(e => JsError(e.getMessage), partId => JsSuccess(EmailSendCreationId(partId)))
    }

  private implicit val emailSendRequestReads: Reads[EmailSendRequest] = Json.reads[EmailSendRequest]

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val blobIdWrites: Writes[BlobId] = Json.valueWrites[BlobId]
  private implicit val threadIdWrites: Writes[ThreadId] = Json.valueWrites[ThreadId]
  private implicit val emailSendIdWrites: Writes[EmailSendId] = Json.valueWrites[EmailSendId]
  private implicit val emailSubmissionIdWrites: Writes[EmailSubmissionId] = Json.valueWrites[EmailSubmissionId]
  private implicit val messageIdWrites: Writes[MessageId] = messageId => JsString(messageId.serialize)
  private implicit val emailSendCreationResponseWrites: Writes[EmailSendCreationResponse] = Json.writes[EmailSendCreationResponse]

  private implicit val emailSendCreatedMapWrites: Writes[Map[EmailSendCreationId, EmailSendCreationResponse]] =
    mapWrites[EmailSendCreationId, EmailSendCreationResponse](creationId => creationId.id.value, emailSendCreationResponseWrites)

  private implicit val emailSendNotCreatedMapWrites: Writes[Map[EmailSendCreationId, SetError]] =
    mapWrites[EmailSendCreationId, SetError](creationId => creationId.id.value, setErrorWrites)

  private implicit val emailSendResponseWrites: Writes[EmailSendResponse] = Json.writes[EmailSendResponse]

  def deserializeEmailSendCreationRequest(input: JsValue): JsResult[EmailSendCreationRequestRaw] =
    Json.fromJson[EmailSendCreationRequestRaw](input)

  def deserializeEmailSendRequest(input: JsValue): JsResult[EmailSendRequest] =
    Json.fromJson[EmailSendRequest](input)

  def deserializeEmailCreationRequest(input: JsValue): JsResult[EmailSubmissionCreationRequest] =
    Json.fromJson[EmailSubmissionCreationRequest](input)

  def serializeEmailSendResponse(emailSendResponse: EmailSendResponse): JsValue =
    Json.toJson(emailSendResponse)

}
