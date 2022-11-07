package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseDeviceToken, FirebaseSubscription, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionGetRequest, FirebaseSubscriptionGetResponse, FirebaseSubscriptionId, FirebaseSubscriptionIds, UnparsedFirebaseSubscriptionId}
import eu.timepit.refined
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.Id.IdConstraint
import org.apache.james.jmap.core.{Properties, UTCDate}
import play.api.libs.json._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object FirebaseSubscriptionSerializer {
  private implicit val unparsedFirebaseSubscriptionIdReads: Reads[UnparsedFirebaseSubscriptionId] = {
    case JsString(string) => refined.refineV[IdConstraint](string)
      .fold(
        e => JsError(s"FirebaseSubscriptionId does not match Id constraints: $e"),
        id => JsSuccess(UnparsedFirebaseSubscriptionId(id)))
    case _ => JsError("FirebaseSubscriptionId needs to be represented by a JsString")
  }

  private implicit val deviceClientIdWrites: Writes[DeviceClientId] = Json.valueWrites[DeviceClientId]
  private implicit val firebaseDeviceTokenWrites: Writes[FirebaseDeviceToken] = Json.valueWrites[FirebaseDeviceToken]
  private implicit val expiresOnWrites: Writes[ZonedDateTime] = date => JsString(UTCDate(date).asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
  private implicit val firebaseSubscriptionExpiredTimeWrites: Writes[FirebaseSubscriptionExpiredTime] = Json.valueWrites[FirebaseSubscriptionExpiredTime]
  private implicit val typeNameWrites: Writes[TypeName] = typeName => JsString(typeName.asString())

  private implicit val firebaseSubscriptionIdWrites: Writes[FirebaseSubscriptionId] = value => JsString(value.serialize)

  // Token value MUST NOT be returned as it might contain private data
  private implicit val firebaseSubscriptionWrites: Writes[FirebaseSubscription] =
    response =>
      Json.obj("id" -> response.id,
        "deviceClientId" -> response.deviceClientId,
        "expires" -> response.expires,
        "types" -> response.types)

  private implicit val idFormat: Format[UnparsedFirebaseSubscriptionId] = Json.valueFormat[UnparsedFirebaseSubscriptionId]
  private implicit val firebaseSubscriptionIdsReads: Reads[FirebaseSubscriptionIds] = Json.valueReads[FirebaseSubscriptionIds]
  private implicit val firebaseSubscriptionGetResponseWrites: Writes[FirebaseSubscriptionGetResponse] = Json.writes[FirebaseSubscriptionGetResponse]
  private implicit val firebaseSubscriptionIdsWrites: Writes[FirebaseSubscriptionIds] = Json.writes[FirebaseSubscriptionIds]

  private implicit val firebaseSubscriptionGetRequestReads: Reads[FirebaseSubscriptionGetRequest] = Json.reads[FirebaseSubscriptionGetRequest]

  def deserializeFirebaseSubscriptionGetRequest(input: JsValue): JsResult[FirebaseSubscriptionGetRequest] = Json.fromJson[FirebaseSubscriptionGetRequest](input)

  def serialize(response: FirebaseSubscriptionGetResponse, properties: Properties): JsValue =
    Json.toJson(response)
      .transform((__ \ "list").json.update {
        case JsArray(underlying) => JsSuccess(JsArray(underlying.map {
          case jsonObject: JsObject => properties
            .filter(jsonObject)
          case jsValue => jsValue
        }))
      }).get
}
