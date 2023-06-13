package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelGetRequest, LabelGetResponse, LabelId, LabelIds, UnparsedLabelId}
import org.apache.james.jmap.core.{Properties, UuidState}
import org.apache.james.jmap.mail.Keyword
import play.api.libs.json.{Format, JsArray, JsObject, JsResult, JsSuccess, JsValue, Json, Reads, Writes, __}

object LabelSerializer {
  private implicit val labelIdFormat: Format[LabelId] = Json.valueFormat[LabelId]
  private implicit val unparsedLabelIdFormat: Format[UnparsedLabelId] = Json.valueFormat[UnparsedLabelId]

  private implicit val labelIdsReads: Reads[LabelIds] = Json.valueReads[LabelIds]
  private implicit val labelGetRequestReads: Reads[LabelGetRequest] = Json.reads[LabelGetRequest]

  private implicit val colorWrites: Writes[Color] = Json.valueWrites[Color]
  private implicit val keywordWrites: Writes[Keyword] = Json.valueWrites[Keyword]
  private implicit val displayNameWrites: Writes[DisplayName] = Json.valueWrites[DisplayName]
  private implicit val labelWrites: Writes[Label] = Json.writes[Label]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val labelGetResponseWrites: Writes[LabelGetResponse] = Json.writes[LabelGetResponse]

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
}
