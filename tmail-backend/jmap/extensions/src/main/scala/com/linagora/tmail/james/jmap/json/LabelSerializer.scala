package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelCreationId, LabelCreationRequest, LabelCreationResponse, LabelGetRequest, LabelGetResponse, LabelId, LabelIds, LabelSetRequest, LabelSetResponse, UnparsedLabelId}
import org.apache.james.jmap.core.{Properties, SetError, UuidState}
import org.apache.james.jmap.json.mapWrites
import org.apache.james.jmap.mail.Keyword
import play.api.libs.json.{Format, JsArray, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes, __}

object LabelSerializer {
  private implicit val labelIdFormat: Format[LabelId] = Json.valueFormat[LabelId]
  private implicit val unparsedLabelIdFormat: Format[UnparsedLabelId] = Json.valueFormat[UnparsedLabelId]

  private implicit val labelIdsReads: Reads[LabelIds] = Json.valueReads[LabelIds]
  private implicit val labelGetRequestReads: Reads[LabelGetRequest] = Json.reads[LabelGetRequest]

  private implicit val labelCreationIdFormat: Format[LabelCreationId] = Json.valueFormat[LabelCreationId]

  private implicit val colorWrites: Writes[Color] = Json.valueWrites[Color]
  private implicit val colorReads: Reads[Color] = {
    case jsString: JsString => Color.validate(jsString.value)
      .fold(e => JsError(e.getMessage),
        color => JsSuccess(color))
    case _ => JsError("Expecting a string as a Color")
  }

  private implicit val keywordWrites: Writes[Keyword] = Json.valueWrites[Keyword]
  private implicit val displayNameFormat: Format[DisplayName] = Json.valueFormat[DisplayName]
  private implicit val labelWrites: Writes[Label] = Json.writes[Label]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val labelGetResponseWrites: Writes[LabelGetResponse] = Json.writes[LabelGetResponse]

  private implicit val mapCreationRequestByLabelCreationId: Reads[Map[LabelCreationId, JsObject]] =
    Reads.mapReads[LabelCreationId, JsObject] { string => Json.valueReads[LabelCreationId].reads(JsString(string)) }

  implicit val labelCreationResponseWrites: Writes[LabelCreationResponse] = Json.writes[LabelCreationResponse]

  private implicit val labelMapSetErrorForCreationWrites: Writes[Map[LabelCreationId, SetError]] =
    mapWrites[LabelCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val labelMapCreationResponseWrites: Writes[Map[LabelCreationId, LabelCreationResponse]] =
    mapWrites[LabelCreationId, LabelCreationResponse](_.id.value, labelCreationResponseWrites)

  implicit val labelCreationRequest: Reads[LabelCreationRequest] = Json.reads[LabelCreationRequest]

  private implicit val labelSetResponseWrites: Writes[LabelSetResponse] = Json.writes[LabelSetResponse]

  private implicit val labelSetRequestReads: Reads[LabelSetRequest] = Json.reads[LabelSetRequest]

  def deserializeGetRequest(input: JsValue): JsResult[LabelGetRequest] = Json.fromJson[LabelGetRequest](input)

  def serializeGetResponse(response: LabelGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => properties
            .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def deserializeLabelSetRequest(input: JsValue): JsResult[LabelSetRequest] = Json.fromJson[LabelSetRequest](input)

  def serializeLabelSetResponse(response: LabelSetResponse): JsValue = Json.toJson(response)
}
