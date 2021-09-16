package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.longlivedtoken.{DeviceId, LongLivedTokenId, LongLivedTokenSecret}
import com.linagora.tmail.james.jmap.model.{LongLivedTokenSetRequest, LongLivedTokenSetResponse, TokenCreateRequest, TokenCreateResponse}
import play.api.libs.json.{Format, JsResult, JsValue, Json, Reads, Writes}

object LongLivedTokenSerializer {

  private implicit val longLivedTokenIdFormat: Format[LongLivedTokenId] = Json.valueFormat[LongLivedTokenId]
  private implicit val longLivedTokenSecretFormat: Format[LongLivedTokenSecret] = Json.valueFormat[LongLivedTokenSecret]
  private implicit val deviceIdFormat: Format[DeviceId] = Json.valueFormat[DeviceId]
  private implicit val createRequestRead: Reads[TokenCreateRequest] = Json.reads[TokenCreateRequest]

  private implicit val longLivedTokenSetRequestRead: Reads[LongLivedTokenSetRequest] = Json.reads[LongLivedTokenSetRequest]
  private implicit val tokenCreateResponseWrite: Writes[TokenCreateResponse] = Json.writes[TokenCreateResponse]
  private implicit val longLivedTokenSetResponseWrite: Writes[LongLivedTokenSetResponse] = Json.writes[LongLivedTokenSetResponse]

  def deserializeLongLivedTokenSetRequest(input: JsValue): JsResult[LongLivedTokenSetRequest] =
    Json.fromJson[LongLivedTokenSetRequest](input)

  def serializeKeystoreGetResponse(response: LongLivedTokenSetResponse): JsValue = Json.toJson(response)

}
