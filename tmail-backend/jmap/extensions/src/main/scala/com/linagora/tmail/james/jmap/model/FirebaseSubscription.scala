package com.linagora.tmail.james.jmap.model

import java.time.ZonedDateTime
import java.util.UUID

import org.apache.james.jmap.api.model.ExpireTimeInvalidException.TIME_FORMATTER
import org.apache.james.jmap.api.model.TypeName

object FirebaseSubscriptionId {
  def generate(): FirebaseSubscriptionId = FirebaseSubscriptionId(UUID.randomUUID)
}

case class FirebaseSubscriptionId(value: UUID) {
  def serialise: String = value.toString
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
                                               types: Seq[TypeName])

object FirebaseSubscription {
  val EXPIRES_TIME_MAX_DAY: Int = 7

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