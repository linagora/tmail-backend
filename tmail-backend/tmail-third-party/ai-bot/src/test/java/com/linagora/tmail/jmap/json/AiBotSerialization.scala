package com.linagora.tmail.jmap.json

import com.linagora.tmail.jmap.mail.{AiBotSuggestReplyRequest, AiBotSuggestReplyResponse}
import play.api.libs.json.{Format, JsResult, JsValue, Json};

object IdentitySerializer {
  private implicit val emailerNameReads: Format[AiBotSuggestReplyRequest] = Json.valueFormat[AiBotSuggestReplyRequest]
  private implicit val identityIdFormat: Format[AiBotSuggestReplyResponse] = Json.valueFormat[AiBotSuggestReplyResponse]

  //To check if this is necessary
  def serializeRequest(request: AiBotSuggestReplyRequest): JsValue = {
    Json.toJson(request)
  }

  def deserializeRequest(json: JsValue): JsResult[AiBotSuggestReplyRequest] = {
    Json.fromJson[AiBotSuggestReplyRequest](json)
  }

  def serializeResponse(response: AiBotSuggestReplyResponse): JsValue = {
    Json.toJson(response)
  }

  // to ckeck if this is necesssary
  def deserializeResponse(json: JsValue): JsResult[AiBotSuggestReplyResponse] = {
    Json.fromJson[AiBotSuggestReplyResponse](json)
  }
}