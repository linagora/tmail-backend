package com.linagora.tmail.james.jmap.model

import eu.timepit.refined.auto._
import eu.timepit.refined.collection.NonEmpty
import eu.timepit.refined.refineV
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.james.jmap.api.model.ExpireTimeInvalidException.TIME_FORMATTER
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{Id, Properties}
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.SetError.SetErrorDescription
import org.apache.james.jmap.core.{Id, Properties, SetError}
import org.apache.james.jmap.method.WithoutAccountId
import play.api.libs.json.{JsObject, JsPath, JsonValidationError}

import java.time.ZonedDateTime
import java.util.UUID
import scala.util.Try

object FirebaseSubscriptionId {
  def generate(): FirebaseSubscriptionId = FirebaseSubscriptionId(UUID.randomUUID)

  def liftOrThrow(value: String): Either[IllegalArgumentException, FirebaseSubscriptionId] =
    Try(UUID.fromString(value))
      .map(value1 => FirebaseSubscriptionId(value1))
      .toEither
      .left.map(e => new IllegalArgumentException("FirebaseSubscriptionId is invalid", e))
}

case class FirebaseSubscriptionId(value: UUID) {
  def serialize: String = value.toString

  def asUnparsedFirebaseSubscriptionId: UnparsedFirebaseSubscriptionId =
    UnparsedFirebaseSubscriptionId(Id.validate(serialize).toOption.get)
}

case class DeviceClientId(value: String) extends AnyVal

case class FirebaseDeviceToken(value: String) extends AnyVal

case class FirebaseSubscriptionExpiredTime(value: ZonedDateTime) {
  def isAfter(date: ZonedDateTime): Boolean = value.isAfter(date)

  def isBefore(date: ZonedDateTime): Boolean = value.isBefore(date)
}

case class FirebaseSubscriptionCreationRequest(deviceClientId: DeviceClientId,
                                               token: FirebaseDeviceToken,
                                               expires: Option[FirebaseSubscriptionExpiredTime] = None,
                                               types: Seq[TypeName]) {

  def validate: Either[IllegalArgumentException, FirebaseSubscriptionCreationRequest] =
    validateTypes

  private def validateTypes: Either[IllegalArgumentException, FirebaseSubscriptionCreationRequest] =
    if (types.isEmpty) {
      scala.Left(new IllegalArgumentException("types must not be empty"))
    } else {
      Right(this)
    }
}

object FirebaseSubscription {
  val EXPIRES_TIME_MAX_DAY: Int = 7
  val allProperties: Properties = Properties("id", "deviceClientId", "expires", "types")
  val idProperty: Properties = Properties("id")

  def from(creationRequest: FirebaseSubscriptionCreationRequest,
           expireTime: FirebaseSubscriptionExpiredTime): FirebaseSubscription =
    FirebaseSubscription(id = FirebaseSubscriptionId.generate(),
      deviceClientId = creationRequest.deviceClientId,
      token = creationRequest.token,
      expires = expireTime,
      types = creationRequest.types)
}

case class FirebaseSubscription(id: FirebaseSubscriptionId,
                                deviceClientId: DeviceClientId,
                                token: FirebaseDeviceToken,
                                expires: FirebaseSubscriptionExpiredTime,
                                types: Seq[TypeName]) {
  def withTypes(types: Seq[TypeName]): FirebaseSubscription = copy(types = types)

  def withExpires(expires: FirebaseSubscriptionExpiredTime): FirebaseSubscription = copy(expires = expires)
}

case class FirebaseSubscriptionNotFoundException(id: FirebaseSubscriptionId) extends RuntimeException

case class ExpireTimeInvalidException(expires: ZonedDateTime, message: String) extends IllegalStateException(s"`${expires.format(TIME_FORMATTER)}` $message")

case class DeviceClientIdInvalidException(deviceClientId: DeviceClientId, message: String) extends IllegalArgumentException(s"`${deviceClientId.value}` $message")

case class TokenInvalidException(message: String) extends IllegalArgumentException(message)

case class UnparsedFirebaseSubscriptionId(id: Id)

case class FirebaseSubscriptionIds(list: List[UnparsedFirebaseSubscriptionId])

case class FirebaseSubscriptionGetRequest(ids: Option[FirebaseSubscriptionIds],
                                          properties: Option[Properties]) extends WithoutAccountId {

  def validateProperties: Either[IllegalArgumentException, Properties] =
    properties match {
      case None => Right(FirebaseSubscription.allProperties)
      case Some(value) =>
        value -- FirebaseSubscription.allProperties match {
          case invalidProperties if invalidProperties.isEmpty() => Right(value ++ FirebaseSubscription.idProperty)
          case invalidProperties: Properties => Left(new IllegalArgumentException(s"The following properties [${invalidProperties.format()}] do not exist."))
        }
    }
}

object FirebaseSubscriptionGetResponse {
  def from(list: Seq[FirebaseSubscription], requestIds: Option[FirebaseSubscriptionIds]): FirebaseSubscriptionGetResponse =
    requestIds match {
      case None => FirebaseSubscriptionGetResponse(list.toList)
      case Some(value) =>
        FirebaseSubscriptionGetResponse(
          list = list.filter(subscription => value.list.contains(subscription.id.asUnparsedFirebaseSubscriptionId)),
          notFound = value.list.diff(list.map(_.id.asUnparsedFirebaseSubscriptionId)).toSet)
    }
}

case class FirebaseSubscriptionGetResponse(list: Seq[FirebaseSubscription],
                                           notFound: Set[UnparsedFirebaseSubscriptionId] = Set())

case class FirebaseSubscriptionCreationId(id: Id) {
  def serialise: String = id.value
}

case class FirebaseSubscriptionSetRequest(create: Option[Map[FirebaseSubscriptionCreationId, JsObject]]) extends WithoutAccountId

case class FirebaseSubscriptionCreationResponse(id: FirebaseSubscriptionId,
                                                expires: Option[FirebaseSubscriptionExpiredTime])

case class FirebaseSubscriptionSetResponse(created: Option[Map[FirebaseSubscriptionCreationId, FirebaseSubscriptionCreationResponse]],
                                           notCreated: Option[Map[FirebaseSubscriptionCreationId, SetError]])

object FirebaseSubscriptionCreationParseException {
  def from(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): FirebaseSubscriptionCreationParseException =
    FirebaseSubscriptionCreationParseException(errors.head match {
      case (path, Seq()) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in FirebaseSubscription object is not valid"))
      case (path, Seq(JsonValidationError(Seq("error.path.missing")))) => SetError.invalidArguments(SetErrorDescription(s"Missing '$path' property in FirebaseSubscription object"))
      case (path, Seq(JsonValidationError(Seq(message)))) => SetError.invalidArguments(SetErrorDescription(s"'$path' property in FirebaseSubscription object is not valid: $message"))
      case (path, _) => SetError.invalidArguments(SetErrorDescription(s"Unknown error on property '$path'"))
    })
}

case class FirebaseSubscriptionCreationParseException(setError: SetError) extends Exception

object FirebaseSubscriptionCreation {
  private val serverSetProperty: Set[String] = Set("id")
  private val assignableProperties: Set[String] = Set("deviceClientId", "token", "expires", "types")
  private val knownProperties: Set[String] = assignableProperties ++ serverSetProperty

  def validateProperties(jsObject: JsObject): Either[FirebaseSubscriptionCreationParseException, JsObject] =
    (jsObject.keys.intersect(serverSetProperty), jsObject.keys.diff(knownProperties)) match {
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