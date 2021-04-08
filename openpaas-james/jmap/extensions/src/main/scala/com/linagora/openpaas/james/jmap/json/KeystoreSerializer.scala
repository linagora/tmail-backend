package com.linagora.openpaas.james.jmap.json

import com.linagora.openpaas.encrypted.KeyId
import com.linagora.openpaas.james.jmap.model.{Key, KeystoreCreationId, KeystoreCreationRequest, KeystoreCreationResponse, KeystoreSetRequest, KeystoreSetResponse}
import eu.timepit.refined.refineV
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.SetError
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsSuccess, JsValue, Json, Reads, Writes}

class KeystoreSerializer {
  implicit val accountId: Format[AccountId] = Json.valueFormat[AccountId]
  implicit val propertiesFormat: Format[Properties] = Json.valueFormat[Properties]
  implicit val setErrorDescriptionWrites: Writes[SetErrorDescription] = Json.valueWrites[SetErrorDescription]
  implicit val setErrorWrites: Writes[SetError] = Json.writes[SetError]

  implicit def writeRefined[T, P, F[_, _]](
                                            implicit writesT: Writes[T],
                                            reftype: RefType[F]
                                          ): Writes[F[T, P]] = Writes(value => writesT.writes(reftype.unwrap(value)))
  implicit def readRefined[T, P, F[_, _]](
                                           implicit readsT: Reads[T],
                                           reftype: RefType[F],
                                           validate: Validate[T, P]
                                         ): Reads[F[T, P]] =
    Reads(jsValue =>
      readsT.reads(jsValue).flatMap { valueT =>
        reftype.refine[P](valueT) match {
          case Right(valueP) => JsSuccess(valueP)
          case Left(error)   => JsError(error)
        }
      })

  private implicit val keyIdFormat: Format[KeyId] = Json.valueFormat[KeyId]

  private implicit val keyReads: Reads[Key] = Json.valueReads[Key]

  implicit val keystoreCreationRequest: Reads[KeystoreCreationRequest] = Json.reads[KeystoreCreationRequest]

  private implicit val mapCreationRequestByKeystoreCreationId: Reads[Map[KeystoreCreationId, JsObject]] =
    Reads.mapReads[KeystoreCreationId, JsObject] {string => refineV[IdConstraint](string)
      .fold(e => JsError(s"mailbox creationId needs to match id contraints: $e"),
        id => JsSuccess(KeystoreCreationId(id))) }

  implicit val keystoreCreationResponseWrites: Writes[KeystoreCreationResponse] = Json.writes[KeystoreCreationResponse]

  private implicit val keystoreMapSetErrorForCreationWrites: Writes[Map[KeystoreCreationId, SetError]] =
    mapWrites[KeystoreCreationId, SetError](_.id.value, setErrorWrites)

  private implicit val keystoreMapCreationResponseWrites: Writes[Map[KeystoreCreationId, KeystoreCreationResponse]] =
    mapWrites[KeystoreCreationId, KeystoreCreationResponse](_.id.value, keystoreCreationResponseWrites)

  private implicit val keystoreSetRequestReads: Reads[KeystoreSetRequest] = Json.reads[KeystoreSetRequest]
  private implicit val keystoreSetResponseWrites: Writes[KeystoreSetResponse] = Json.writes[KeystoreSetResponse]

  def serializeKeystoreSetResponse(response: KeystoreSetResponse): JsValue = Json.toJson(response)

  def deserializeKeystoreCreationRequest(input: JsValue): JsResult[KeystoreCreationRequest] = Json.fromJson[KeystoreCreationRequest](input)

  def deserializeKeystoreSetRequest(input: JsValue): JsResult[KeystoreSetRequest] = Json.fromJson[KeystoreSetRequest](input)
}
