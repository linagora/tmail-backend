package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseToken, FirebaseSubscription, FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionCreationResponse, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionGetRequest, FirebaseSubscriptionGetResponse, FirebaseSubscriptionId, FirebaseSubscriptionIds, FirebaseSubscriptionPatchObject, FirebaseSubscriptionSetRequest, FirebaseSubscriptionSetResponse, FirebaseSubscriptionUpdateResponse, UnparsedFirebaseSubscriptionId}
import eu.timepit.refined
import eu.timepit.refined.refineV
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, SetError, UTCDate}
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json._

import java.time.format.DateTimeFormatter
import javax.inject.Inject

class FirebaseSubscriptionSerializer @Inject()(typeStateFactory: TypeStateFactory) {
  private implicit val unparsedFirebaseSubscriptionIdReads: Reads[UnparsedFirebaseSubscriptionId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"FirebaseSubscriptionId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedFirebaseSubscriptionId(id)))
    case _ => JsError("FirebaseSubscriptionId needs to be represented by a JsString")
  }

  private implicit val deviceClientIdFormat: Format[DeviceClientId] = Json.valueFormat[DeviceClientId]
  private implicit val firebaseDeviceTokenFormat: Format[FirebaseToken] = Json.valueFormat[FirebaseToken]
  private implicit val firebaseSubscriptionExpiredTimeWrites: Writes[FirebaseSubscriptionExpiredTime] = expiredTime => JsString(UTCDate(expiredTime.value)
    .asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
  private implicit val typeNameWrites: Writes[TypeName] = typeName => JsString(typeName.asString())

  private implicit val firebaseSubscriptionIdWrites: Writes[FirebaseSubscriptionId] = value => JsString(value.serialize)

  // Token value MUST NOT be returned as it might contain private data
  private implicit val firebaseSubscriptionWrites: Writes[FirebaseSubscription] =
    response =>
      Json.obj("id" -> response.id,
        "deviceClientId" -> response.deviceClientId,
        "expires" -> response.expires,
        "types" -> response.types)

  private implicit val mapCreationRequestBySubscriptionCreationId: Reads[Map[FirebaseSubscriptionCreationId, JsObject]] =
    Reads.mapReads[FirebaseSubscriptionCreationId, JsObject] { string =>
      refineV[IdConstraint](string)
        .fold(e => JsError(s"firebase subscription id needs to match id constraints: $e"),
          id => JsSuccess(FirebaseSubscriptionCreationId(id)))
    }


  private implicit val patchObject: Reads[FirebaseSubscriptionPatchObject] = Json.valueReads[FirebaseSubscriptionPatchObject]

  private implicit val mapUpdateRequestBySubscriptionCreationId: Reads[Map[UnparsedFirebaseSubscriptionId, FirebaseSubscriptionPatchObject]] =
    Reads.mapReads[UnparsedFirebaseSubscriptionId, FirebaseSubscriptionPatchObject] { string =>
      refineV[IdConstraint](string)
        .fold(e => JsError(s"FirebaseSubscription Id needs to match id constraints: $e"),
          id => JsSuccess(UnparsedFirebaseSubscriptionId(id)))
    }

  private implicit val subscriptionMapUpdateResponseWrites: Writes[Map[FirebaseSubscriptionId, FirebaseSubscriptionUpdateResponse]] =
    mapWrites[FirebaseSubscriptionId, FirebaseSubscriptionUpdateResponse](_.serialize, _ => JsObject.empty)

  private implicit val idFormat: Format[UnparsedFirebaseSubscriptionId] = Json.valueFormat[UnparsedFirebaseSubscriptionId]
  private implicit val firebaseSubscriptionIdsReads: Reads[FirebaseSubscriptionIds] = Json.valueReads[FirebaseSubscriptionIds]
  private implicit val firebaseSubscriptionGetResponseWrites: Writes[FirebaseSubscriptionGetResponse] = Json.writes[FirebaseSubscriptionGetResponse]
  private implicit val firebaseSubscriptionIdsWrites: Writes[FirebaseSubscriptionIds] = Json.writes[FirebaseSubscriptionIds]

  private implicit val firebaseSubscriptionGetRequestReads: Reads[FirebaseSubscriptionGetRequest] = Json.reads[FirebaseSubscriptionGetRequest]

  private implicit val firebaseSubscriptionSetRequestReads: Reads[FirebaseSubscriptionSetRequest] = Json.reads[FirebaseSubscriptionSetRequest]
  private implicit val firebaseSubscriptionCreationResponseWrites: Writes[FirebaseSubscriptionCreationResponse] = Json.writes[FirebaseSubscriptionCreationResponse]
  private implicit val subscriptionMapSetErrorForCreationWrites: Writes[Map[FirebaseSubscriptionCreationId, SetError]] =
    mapWrites[FirebaseSubscriptionCreationId, SetError](_.serialise, setErrorWrites)

  private implicit val subscriptionMapCreationResponseWrites: Writes[Map[FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationResponse]] =
    mapWrites[FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationResponse](_.serialise, firebaseSubscriptionCreationResponseWrites)

  private implicit val firebaseSubscriptionSetResponseWrites: OWrites[FirebaseSubscriptionSetResponse] = Json.writes[FirebaseSubscriptionSetResponse]
  private implicit val subscriptionExpiredTimeReads: Reads[FirebaseSubscriptionExpiredTime] = Json.valueReads[FirebaseSubscriptionExpiredTime]

  private implicit val typeNameReads: Reads[TypeName] = {
    case JsString(serializeValue) => typeStateFactory.parse(serializeValue)
      .fold(e => JsError(e.getMessage), v => JsSuccess(v))
    case _ => JsError()
  }
  private implicit val subscriptionSetRequestReads: Reads[FirebaseSubscriptionCreationRequest] = Json.reads[FirebaseSubscriptionCreationRequest]

  def deserializeFirebaseSubscriptionGetRequest(input: JsValue): JsResult[FirebaseSubscriptionGetRequest] = Json.fromJson[FirebaseSubscriptionGetRequest](input)

  def deserializeFirebaseSubscriptionSetRequest(input: JsValue): JsResult[FirebaseSubscriptionSetRequest] = Json.fromJson[FirebaseSubscriptionSetRequest](input)

  def deserializeFirebaseSubscriptionCreationRequest(input: JsValue): JsResult[FirebaseSubscriptionCreationRequest] = Json.fromJson[FirebaseSubscriptionCreationRequest](input)

  def serialize(response: FirebaseSubscriptionGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => properties
            .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get

  def serialize(firebaseSubscriptionSetResponse: FirebaseSubscriptionSetResponse): JsObject = Json.toJsObject(firebaseSubscriptionSetResponse)
}
