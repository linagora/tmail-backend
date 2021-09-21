package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.longlivedtoken.{AuthenticationToken, DeviceId, LongLivedTokenId, LongLivedTokenSecret}
import com.linagora.tmail.james.jmap.model.{LongLivedTokenCreationId, LongLivedTokenCreationRequest, LongLivedTokenSetRequest, LongLivedTokenSetResponse, TokenCreationResult}
import org.apache.james.jmap.core.{Id, SetError}
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, Reads, Writes}

object LongLivedTokenSerializer {

  private implicit val creationIdFormat: Format[LongLivedTokenCreationId] = Json.valueFormat[LongLivedTokenCreationId]
  private implicit val longLivedTokenIdFormat: Format[LongLivedTokenId] = Json.valueFormat[LongLivedTokenId]
  private implicit val longLivedTokenSecretFormat: Format[LongLivedTokenSecret] = Json.valueFormat[LongLivedTokenSecret]
  private implicit val deviceIdFormat: Format[DeviceId] = Json.valueFormat[DeviceId]
  private implicit val createRequestRead: Reads[LongLivedTokenCreationRequest] = Json.reads[LongLivedTokenCreationRequest]

  private implicit val mapCreationIdAndObjectReads: Reads[Map[LongLivedTokenCreationId, JsObject]] =
    Reads.mapReads[LongLivedTokenCreationId, JsObject] {
      s => Id.validate(s).fold(e => JsError(e.getMessage), partId => JsSuccess(LongLivedTokenCreationId(partId)))
    }
  private implicit val authenticationTokenWrite: Writes[AuthenticationToken] = authenToken => JsString(authenToken.username.asString() + "_" + authenToken.secret.value.toString)
  private implicit val tokenCreateResponseWrite: Writes[TokenCreationResult] = Json.writes[TokenCreationResult]
  private implicit val tokenCreatedMapWrites: Writes[Map[LongLivedTokenCreationId, TokenCreationResult]] =
    mapWrites[LongLivedTokenCreationId, TokenCreationResult](creationId => creationId.id.value, tokenCreateResponseWrite)

  private implicit val tokenNotCreatedMapWrites: Writes[Map[LongLivedTokenCreationId, SetError]] =
    mapWrites[LongLivedTokenCreationId, SetError](creationId => creationId.id.value, setErrorWrites)

  private implicit val longLivedTokenSetRequestRead: Reads[LongLivedTokenSetRequest] = Json.reads[LongLivedTokenSetRequest]
  private implicit val longLivedTokenSetResponseWrite: Writes[LongLivedTokenSetResponse] = Json.writes[LongLivedTokenSetResponse]

  def deserializeLongLivedTokenSetRequest(input: JsValue): JsResult[LongLivedTokenSetRequest] =
    Json.fromJson[LongLivedTokenSetRequest](input)

  def deserializeLongLivedTokenCreationRequest(input: JsValue): JsResult[LongLivedTokenCreationRequest] =
    Json.fromJson[LongLivedTokenCreationRequest](input)

  def serializeLongLivedTokenSetResponse(response: LongLivedTokenSetResponse): JsValue = Json.toJson(response)

}
