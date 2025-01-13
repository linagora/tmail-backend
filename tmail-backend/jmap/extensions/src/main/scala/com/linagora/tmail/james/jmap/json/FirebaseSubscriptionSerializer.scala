/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.json

import com.linagora.tmail.james.jmap.method.{ApiKey, AppId, AuthDomain, DatabaseUrl, FirebaseCapabilityProperties, MessagingSenderId, ProjectId, StorageBucket, VapidPublicKey}
import com.linagora.tmail.james.jmap.model.{DeviceClientId, FirebaseSubscription, FirebaseSubscriptionCreation, FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationParseException, FirebaseSubscriptionCreationRequest, FirebaseSubscriptionCreationResponse, FirebaseSubscriptionExpiredTime, FirebaseSubscriptionGetRequest, FirebaseSubscriptionGetResponse, FirebaseSubscriptionId, FirebaseSubscriptionIds, FirebaseSubscriptionPatchObject, FirebaseSubscriptionSetRequest, FirebaseSubscriptionSetResponse, FirebaseSubscriptionUpdateResponse, FirebaseToken, UnparsedFirebaseSubscriptionId}
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import jakarta.inject.Inject
import org.apache.james.jmap.api.change.TypeStateFactory
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Properties, SetError, UTCDate}
import org.apache.james.jmap.json.mapWrites
import play.api.libs.json._

object FirebaseSubscriptionSerializer {
  private implicit val apiKeyWrites: Writes[ApiKey] = Json.valueWrites[ApiKey]
  private implicit val apiIdWrites: Writes[AppId] = Json.valueWrites[AppId]
  private implicit val messagingSenderIdWrites: Writes[MessagingSenderId] = Json.valueWrites[MessagingSenderId]
  private implicit val projectIdWrites: Writes[ProjectId] = Json.valueWrites[ProjectId]
  private implicit val databaseUrlWrites: Writes[DatabaseUrl] = Json.valueWrites[DatabaseUrl]
  private implicit val storageBucketWrites: Writes[StorageBucket] = Json.valueWrites[StorageBucket]
  private implicit val authDomainWrites: Writes[AuthDomain] = Json.valueWrites[AuthDomain]
  private implicit val vapidPublicKeyWrites: Writes[VapidPublicKey] = Json.valueWrites[VapidPublicKey]
  val firebaseCapabilityWrites: OWrites[FirebaseCapabilityProperties] = Json.writes[FirebaseCapabilityProperties]
}

class FirebaseSubscriptionSerializer @Inject()(typeStateFactory: TypeStateFactory) {
  private implicit val unparsedFirebaseSubscriptionIdReads: Reads[UnparsedFirebaseSubscriptionId] = Json.valueReads[UnparsedFirebaseSubscriptionId]

  private implicit val deviceClientIdFormat: Format[DeviceClientId] = Json.valueFormat[DeviceClientId]
  private implicit val firebaseDeviceTokenFormat: Format[FirebaseToken] = Json.valueFormat[FirebaseToken]
  private implicit val firebaseSubscriptionExpiredTimeWrites: Writes[FirebaseSubscriptionExpiredTime] = expiredTime => JsString(UTCDate(expiredTime.value)
    .asUTC.format(dateTimeUTCFormatter))
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
      Json.valueReads[FirebaseSubscriptionCreationId].reads(JsString(string))
    }

  private implicit val patchObject: Reads[FirebaseSubscriptionPatchObject] = Json.valueReads[FirebaseSubscriptionPatchObject]

  private implicit val mapUpdateRequestBySubscriptionCreationId: Reads[Map[UnparsedFirebaseSubscriptionId, FirebaseSubscriptionPatchObject]] =
    Reads.mapReads[UnparsedFirebaseSubscriptionId, FirebaseSubscriptionPatchObject] { string =>
      unparsedFirebaseSubscriptionIdReads.reads(JsString(string))}

  private implicit val firebaseSubscriptionUpdateResponseWrites :Writes[FirebaseSubscriptionUpdateResponse] = Json.writes[FirebaseSubscriptionUpdateResponse]

  private implicit val subscriptionMapUpdateResponseWrites: Writes[Map[FirebaseSubscriptionId, FirebaseSubscriptionUpdateResponse]] =
    mapWrites[FirebaseSubscriptionId, FirebaseSubscriptionUpdateResponse](_.serialize, firebaseSubscriptionUpdateResponseWrites)

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
    case JsString(serializeValue) => typeStateFactory.strictParse(serializeValue)
      .fold(e => JsError(e.getMessage), v => JsSuccess(v))
    case _ => JsError()
  }
  private implicit val subscriptionSetRequestReads: Reads[FirebaseSubscriptionCreationRequest] = Json.reads[FirebaseSubscriptionCreationRequest]

  def deserializeFirebaseSubscriptionGetRequest(input: JsValue): JsResult[FirebaseSubscriptionGetRequest] = Json.fromJson[FirebaseSubscriptionGetRequest](input)

  def deserializeFirebaseSubscriptionSetRequest(input: JsValue): JsResult[FirebaseSubscriptionSetRequest] = Json.fromJson[FirebaseSubscriptionSetRequest](input)

  private val subscriptionCreationRequestStandardReads: Reads[FirebaseSubscriptionCreationRequest] = Json.reads[FirebaseSubscriptionCreationRequest]

  implicit val subscriptionCreationRequestReads: Reads[FirebaseSubscriptionCreationRequest] = new Reads[FirebaseSubscriptionCreationRequest] {
    override def reads(json: JsValue): JsResult[FirebaseSubscriptionCreationRequest] =
      subscriptionCreationRequestStandardReads.reads(json)
        .flatMap(request => {
          validateProperties(json.as[JsObject])
            .fold(_ => JsError("Failed to validate properties"), _ => JsSuccess(request))
        })

    def validateProperties(jsObject: JsObject): Either[FirebaseSubscriptionCreationParseException, JsObject] =
      (jsObject.keys.intersect(FirebaseSubscriptionCreation.serverSetProperty), jsObject.keys.diff(FirebaseSubscriptionCreation.knownProperties)) match {
        case (_, unknownProperties) if unknownProperties.nonEmpty =>
          Left(FirebaseSubscriptionCreationParseException(SetError.invalidArguments(
            SetErrorDescription("Some unknown properties were specified"),
            Some(toProperties(unknownProperties.toSet)))))
        case (specifiedServerSetProperties, _) if specifiedServerSetProperties.nonEmpty =>
          Left(FirebaseSubscriptionCreationParseException(SetError.invalidArguments(
            SetErrorDescription("Some server-set properties were specified"),
            Some(toProperties(specifiedServerSetProperties.toSet)))))
        case _ => scala.Right(jsObject)
      }

    private def toProperties(strings: Set[String]): Properties = Properties(strings
      .flatMap(string => {
        val refinedValue: Either[String, NonEmptyString] = refineV[NonEmpty](string)
        refinedValue.fold(_ => None, Some(_))
      }))
  }

  def deserializeFirebaseSubscriptionCreationRequest(input: JsValue): JsResult[FirebaseSubscriptionCreationRequest] = Json.fromJson[FirebaseSubscriptionCreationRequest](input)(subscriptionCreationRequestReads)

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
