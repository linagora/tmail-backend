package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{HasMoreChanges, LabelChangesRequest, LabelChangesResponse, LabelId}
import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.UuidState
import play.api.libs.json.{Format, JsError, JsNumber, JsObject, JsResult, JsSuccess, JsValue, Json, Reads, Writes}

object LabelChangesSerializer {
  private implicit val labelIdFormat: Format[LabelId] = Json.valueFormat[LabelId]
  private implicit val limitReads: Reads[Limit] = {
    case JsNumber(value) => JsSuccess(Limit.of(value.intValue))
    case _ => JsError("Limit must be an integer")
  }
  private implicit val stateReads: Reads[UuidState] = Json.valueReads[UuidState]
  private implicit val labelChangesRequestReads: Reads[LabelChangesRequest] = Json.reads[LabelChangesRequest]
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val hasMoreChangesWrites: Writes[HasMoreChanges] = Json.valueWrites[HasMoreChanges]
  private implicit val labelChangesResponseWrites: Writes[LabelChangesResponse] =
    response => Json.obj(
      "accountId" -> response.accountId,
      "oldState" -> response.oldState,
      "newState" -> response.newState,
      "hasMoreChanges" -> response.hasMoreChanges,
      "created" -> response.created,
      "updated" -> response.updated,
      "destroyed" -> response.destroyed)

  def deserializeRequest(input: JsValue): JsResult[LabelChangesRequest] = Json.fromJson[LabelChangesRequest](input)

  def serialize(response: LabelChangesResponse): JsObject =
    Json.toJson(response).as[JsObject]
}
