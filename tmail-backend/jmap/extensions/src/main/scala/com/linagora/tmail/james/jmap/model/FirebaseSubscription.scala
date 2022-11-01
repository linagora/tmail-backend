package com.linagora.tmail.james.jmap.model

import java.time.ZonedDateTime
import java.util.UUID

import org.apache.james.jmap.api.model.TypeName

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

case class FirebaseSubscription(id: FirebaseSubscriptionId,
                                deviceClientId: DeviceClientId,
                                token: FirebaseDeviceToken,
                                expires: FirebaseSubscriptionExpiredTime,
                                types: Seq[TypeName])