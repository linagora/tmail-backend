package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.encrypted.{KeyId, PublicKey}
import com.linagora.tmail.james.jmap.model.{Key, KeystoreCreationId, KeystoreCreationRequest, KeystoreCreationResponse, KeystoreGetRequest, KeystoreGetResponse, KeystoreSetRequest, KeystoreSetResponse}
import eu.timepit.refined.refineV
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{SetError, UuidState}
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsSuccess, JsValue, Json, Reads, Writes}

class KeystoreSerializer {
  private implicit val keyIdFormat: Format[KeyId] = Json.valueFormat[KeyId]

  private implicit val keyReads: Reads[Key] = Json.valueReads[Key]

  implicit val keystoreCreationRequest: Reads[KeystoreCreationRequest] = Json.reads[KeystoreCreationRequest]

  private implicit val mapCreationRequestByKeystoreCreationId: Reads[Map[KeystoreCreationId, JsObject]] =
    Reads.mapReads[KeystoreCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"key creationId needs to match id constraints: $e"),
        id => JsSuccess(KeystoreCreationId(id))) }

  implicit val keystoreCreationResponseWrites: Writes[KeystoreCreationResponse] = Json.writes[KeystoreCreationResponse]

  private implicit val keystoreMapSetErrorForCreationWrites: Writes[Map[KeystoreCreationId, SetError]] =
    mapWrites[KeystoreCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val keystoreMapCreationResponseWrites: Writes[Map[KeystoreCreationId, KeystoreCreationResponse]] =
    mapWrites[KeystoreCreationId, KeystoreCreationResponse](_.id.value, keystoreCreationResponseWrites)

  private implicit val payloadWrites: Writes[Array[Byte]] = (payload: Array[Byte]) => Json.toJson(new String(payload))
  private implicit val stateWrites: Writes[UuidState] = Json.valueWrites[UuidState]
  private implicit val publicKeyWrites: Writes[PublicKey] = Json.writes[PublicKey]
  private implicit val keystoreMapGetResponseWrites: Writes[Map[KeyId, PublicKey]] = mapWrites[KeyId, PublicKey](_.value, publicKeyWrites)

  private implicit val keyIdReads: Format[KeystoreCreationId] = Json.valueFormat[KeystoreCreationId]
  private implicit val keystoreDestroyReads: Reads[List[KeystoreCreationId]] = Reads.list[KeystoreCreationId]
  private implicit val keystoreSetRequestReads: Reads[KeystoreSetRequest] = Json.reads[KeystoreSetRequest]
  private implicit val keystoreSetResponseWrites: Writes[KeystoreSetResponse] = Json.writes[KeystoreSetResponse]
  private implicit val keystoreGetRequestReads: Reads[KeystoreGetRequest] = Json.reads[KeystoreGetRequest]
  private implicit val keystoreGetResponseWrites: Writes[KeystoreGetResponse] = Json.writes[KeystoreGetResponse]

  def serializeKeystoreSetResponse(response: KeystoreSetResponse): JsValue = Json.toJson(response)

  def deserializeKeystoreCreationRequest(input: JsValue): JsResult[KeystoreCreationRequest] = Json.fromJson[KeystoreCreationRequest](input)

  def deserializeKeystoreSetRequest(input: JsValue): JsResult[KeystoreSetRequest] = Json.fromJson[KeystoreSetRequest](input)

  def deserializeKeystoreGetRequest(input: JsValue): JsResult[KeystoreGetRequest] = Json.fromJson[KeystoreGetRequest](input)

  def serializeKeystoreGetResponse(response: KeystoreGetResponse): JsValue = Json.toJson(response)
}
