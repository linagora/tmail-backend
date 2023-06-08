package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{Forward, ForwardGetRequest, ForwardGetResponse, ForwardIds, ForwardNotFound, ForwardSetError, ForwardSetPatchObject, ForwardSetRequest, ForwardSetResponse, ForwardSetUpdateResponse, ForwardUpdateRequest, Forwards, LocalCopy, UnparsedForwardId}
import org.apache.james.jmap.core.{Properties, UuidState}
import play.api.libs.json._

object ForwardSerializer {
  private implicit val unparsedForwardIdWrites: Writes[UnparsedForwardId] = Json.valueWrites[UnparsedForwardId]
  private implicit val unparsedForwardIdReads: Reads[UnparsedForwardId] = Json.valueReads[UnparsedForwardId]
  private implicit val forwardIdsReads: Reads[ForwardIds] = Json.valueReads[ForwardIds]

  private implicit val forwardGetRequestReads: Reads[ForwardGetRequest] = Json.reads[ForwardGetRequest]

  private implicit val localCopyFormats: Format[LocalCopy] = Json.valueFormat[LocalCopy]
  private implicit val forwardWrites: Writes[Forward] = Json.valueWrites[Forward]
  private implicit val forwardsWrites: Writes[Forwards] = Json.writes[Forwards]

  private implicit val forwardNotFoundWrites: Writes[ForwardNotFound] =
    notFound => JsArray(notFound.value.toList.map(id => JsString(id.id.value)))

  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]

  private implicit val forwardGetResponseWrites: Writes[ForwardGetResponse] = Json.writes[ForwardGetResponse]

  private implicit val forwardReads: Reads[Forward] = Json.valueReads[Forward]
  private implicit val forwardSetPatchObjectReads: Reads[ForwardSetPatchObject] = {
    case jsObject: JsObject => JsSuccess(ForwardSetPatchObject(jsObject))
    case _ => JsError("ForwardSetPatchObject needs to be represented by a JsObject")
  }
  private implicit val forwardSetRequestReads: Reads[ForwardSetRequest] = Json.reads[ForwardSetRequest]
  private implicit val forwardUpdateRequestReads: Reads[ForwardUpdateRequest] = Json.reads[ForwardUpdateRequest]
  private implicit val forwardSetUpdateResponseWrites: Writes[ForwardSetUpdateResponse] = Json.valueWrites[ForwardSetUpdateResponse]
  private implicit val forwardSetErrorWrites: Writes[ForwardSetError] = Json.writes[ForwardSetError]
  private implicit val forwardSetResponseWrites: Writes[ForwardSetResponse] = Json.writes[ForwardSetResponse]

  def serializeForwardGetResponse(forwardGetResponse: ForwardGetResponse)(implicit forwardsWrites: Writes[Forwards]): JsValue =
    serializeForwardGetResponse(forwardGetResponse, Forwards.allProperties)

  def serializeForwardGetResponse(forwardGetResponse: ForwardGetResponse, properties: Properties): JsValue =
    Json.toJson(forwardGetResponse)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject =>
            Forwards.propertiesFiltered(properties)
              .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def serializeForwardSetResponse(response: ForwardSetResponse): JsValue = Json.toJson(response)

  def deserializeForwardGetRequest(input: JsValue): JsResult[ForwardGetRequest] = Json.fromJson[ForwardGetRequest](input)

  def deserializeForwardSetRequest(input: JsValue): JsResult[ForwardSetRequest] = Json.fromJson[ForwardSetRequest](input)

  def deserializeForwardSetUpdateRequest(input: JsValue): JsResult[ForwardUpdateRequest] = Json.fromJson[ForwardUpdateRequest](input)

}
