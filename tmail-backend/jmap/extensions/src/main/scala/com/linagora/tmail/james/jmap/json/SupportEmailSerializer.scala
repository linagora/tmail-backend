package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{SupportEmailGetRequest, SupportEmailGetResponse}
import play.api.libs.json.{JsResult, JsValue, Json, Reads, Writes}

object SupportEmailSerializer {
  private implicit val supportEmailRequestReads: Reads[SupportEmailGetRequest] = Reads.pure(SupportEmailGetRequest())
  private implicit val supportEmailResponseWrites : Writes[SupportEmailGetResponse] = Json.writes[SupportEmailGetResponse]

  def deserializeGetRequest(input: JsValue): JsResult[SupportEmailGetRequest] = Json.fromJson[SupportEmailGetRequest](input)

  def serializeGetResponse(response: SupportEmailGetResponse): JsValue = Json.toJson(response)
}
