package com.linagora.tmail.james.jmap.model

import java.time.ZonedDateTime
import java.util.UUID

import eu.timepit.refined.auto._
import org.apache.james.jmap.api.model.ExpireTimeInvalidException.TIME_FORMATTER
import org.apache.james.jmap.api.model.TypeName
import org.apache.james.jmap.core.Id.Id
import org.apache.james.jmap.core.{Id, Properties}
import org.apache.james.jmap.method.WithoutAccountId

import scala.util.Try

object FirebaseSubscriptionId {
  def generate(): FirebaseSubscriptionId = FirebaseSubscriptionId(UUID.randomUUID)

  def liftOrThrow(unparsedId: UnparsedFirebaseSubscriptionId): Either[IllegalArgumentException, FirebaseSubscriptionId] =
    liftOrThrow(unparsedId.id.value)

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

case class FirebaseToken(value: String) extends AnyVal

case class FirebaseSubscriptionExpiredTime(value: ZonedDateTime) {
  def isAfter(date: ZonedDateTime): Boolean = value.isAfter(date)

  def isBefore(date: ZonedDateTime): Boolean = value.isBefore(date)
}

object FirebaseSubscriptionCreation {
  val serverSetProperty: Set[String] = Set("id")
  val assignableProperties: Set[String] = Set("deviceClientId", "token", "expires", "types")
  val knownProperties: Set[String] = assignableProperties ++ serverSetProperty
}

case class FirebaseSubscriptionCreationRequest(deviceClientId: DeviceClientId,
                                               token: FirebaseToken,
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
                                token: FirebaseToken,
                                expires: FirebaseSubscriptionExpiredTime,
                                types: Seq[TypeName]) {
  def withTypes(types: Seq[TypeName]): FirebaseSubscription = copy(types = types)

  def withExpires(expires: FirebaseSubscriptionExpiredTime): FirebaseSubscription = copy(expires = expires)
}

case class FirebaseSubscriptionNotFoundException(id: FirebaseSubscriptionId) extends RuntimeException

case class ExpireTimeInvalidException(expires: ZonedDateTime, message: String) extends IllegalStateException(s"`${expires.format(TIME_FORMATTER)}` $message")

case class DeviceClientIdInvalidException(deviceClientId: DeviceClientId, message: String) extends IllegalArgumentException(s"`${deviceClientId.value}` $message")

case class TokenInvalidException(message: String) extends IllegalArgumentException(message)

case class MissingOrInvalidFirebaseCredentialException(message: String) extends IllegalArgumentException(message)

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