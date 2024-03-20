package com.linagora.tmail.rate.limiter.api

import java.time.Duration
import java.util.UUID

import com.linagora.tmail.rate.limiter.api.LimitTypes.{COUNT, LimitTypes, SIZE}
import com.linagora.tmail.rate.limiter.api.OperationLimitations.{DELIVERY_LIMITATIONS_NAME, RELAY_LIMITATIONS_NAME, TRANSIT_LIMITATIONS_NAME}
import com.linagora.tmail.rate.limiter.api.OperationLimitationsType.OperationLimitationsType
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanName.RateLimitingPlanName
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.core.Username
import org.apache.james.rate.limiter.api.AllowedQuantity
import org.apache.james.rate.limiter.api.AllowedQuantity.AllowedQuantity

object LimitTypes {
  val SIZE: String = "size"
  val COUNT: String = "count"

  type LimitTypes = Set[LimitType] Refined NonEmpty

  def validate(value: Set[LimitType]): Either[IllegalArgumentException, LimitTypes] =
    refined.refineV[NonEmpty](value) match {
      case Right(value) => Right(value)
      case Left(_) => Left(new IllegalArgumentException("value should not be empty"))
    }

  def liftOrThrow(value: Set[LimitType]): LimitTypes =
    validate(value) match {
      case Right(value) => value
      case Left(error) => throw error
    }

  def from(limitTypeAndAllowedQuantity: Map[String, Long]): LimitTypes = {
    val limitTypes: Set[LimitType] = limitTypeAndAllowedQuantity
      .map(map => LimitType.liftOrThrow(map._1, map._2))
      .toSet
    liftOrThrow(limitTypes)
  }

  def fromMutableMap(limitTypeAndAllowedQuantity: scala.collection.mutable.Map[String, Long]): LimitTypes =
    from(limitTypeAndAllowedQuantity.toMap)
}

object LimitType {
  def liftOrThrow(limitTypeName: String, allowedQuantity: Long): LimitType =
    from(limitTypeName, allowedQuantity) match {
      case Right(value) => value
      case Left(error) => throw error
    }

  def from(limitTypeName: String, allowedQuantity: Long): Either[IllegalArgumentException, LimitType] = {
    limitTypeName match {
      case SIZE => AllowedQuantity.validate(allowedQuantity).map(Size)
      case COUNT => AllowedQuantity.validate(allowedQuantity).map(Count)
      case _ => Left(new IllegalArgumentException(s"`$limitTypeName` is invalid"))
    }
  }
}

sealed trait LimitType {
  def asString(): String

  def allowedQuantity(): AllowedQuantity
}

case class Count(quantity: AllowedQuantity) extends LimitType {
  override def asString(): String = COUNT

  override def allowedQuantity(): AllowedQuantity = quantity
}

case class Size(quantity: AllowedQuantity) extends LimitType {
  override def asString(): String = SIZE

  override def allowedQuantity(): AllowedQuantity = quantity
}

case class RateLimitation(name: String, period: Duration, limits: LimitTypes) {
  def limitsValue: Set[LimitType] = limits.value
}

object RateLimitingPlanId {
  def generate: RateLimitingPlanId = RateLimitingPlanId(UUID.randomUUID())

  def parse(string: String): RateLimitingPlanId = RateLimitingPlanId(UUID.fromString(string))
}

case class RateLimitingPlanId(value: UUID) {
  def serialize(): String = value.toString
}

object RateLimitingPlanName {
  type RateLimitingPlanName = String Refined NonEmpty

  def liftOrThrow(value: String): RateLimitingPlanName =
    refined.refineV[NonEmpty](value) match {
      case Right(value) => value
      case Left(_) => throw new IllegalArgumentException("Rate limiting plan name should not be empty")
    }
}

object OperationLimitationsType {
  type OperationLimitationsType = Seq[OperationLimitations] Refined NonEmpty

  def liftOrThrow(value: Seq[OperationLimitations]): OperationLimitationsType =
    refined.refineV[NonEmpty](value) match {
      case Right(value) => value
      case Left(_) => throw new IllegalArgumentException("value should not be empty")
    }
}

object OperationLimitations {
  val TRANSIT_LIMITATIONS_NAME: String = "TransitLimitations"
  val RELAY_LIMITATIONS_NAME: String = "RelayLimitations"
  val DELIVERY_LIMITATIONS_NAME: String = "DeliveryLimitations"

  def liftOrThrow(operationName: String, rateLimitations: Seq[RateLimitation]): OperationLimitations =
    from(operationName, rateLimitations) match {
      case Right(value) => value
      case Left(error) => throw error
    }

  def from(operationName: String, rateLimitations: Seq[RateLimitation]): Either[IllegalArgumentException, OperationLimitations] =
    operationName match {
      case TRANSIT_LIMITATIONS_NAME => Right(TransitLimitations(rateLimitations))
      case RELAY_LIMITATIONS_NAME => Right(RelayLimitations(rateLimitations))
      case DELIVERY_LIMITATIONS_NAME => Right(DeliveryLimitations(rateLimitations))
      case _ => Left(new IllegalArgumentException(s"`$operationName` is invalid"))
    }
}

sealed trait OperationLimitations {
  def asString(): String

  def rateLimitations(): Seq[RateLimitation]
}

case class TransitLimitations(value: Seq[RateLimitation]) extends OperationLimitations {
  override def asString(): String = TRANSIT_LIMITATIONS_NAME

  override def rateLimitations(): Seq[RateLimitation] = value
}

case class RelayLimitations(value: Seq[RateLimitation]) extends OperationLimitations {
  override def asString(): String = RELAY_LIMITATIONS_NAME

  override def rateLimitations(): Seq[RateLimitation] = value
}

case class DeliveryLimitations(value: Seq[RateLimitation]) extends OperationLimitations {
  override def asString(): String = DELIVERY_LIMITATIONS_NAME

  override def rateLimitations(): Seq[RateLimitation] = value
}

object RateLimitingPlan {
  def from(createRequest: RateLimitingPlanCreateRequest): RateLimitingPlan =
    RateLimitingPlan(id = RateLimitingPlanId.generate, name = createRequest.name, operationLimitations = createRequest.operationLimitations.value)

  def update(origin: RateLimitingPlan, resetRequest: RateLimitingPlanResetRequest): RateLimitingPlan =
    origin.copy(name = resetRequest.name, operationLimitations = resetRequest.operationLimitations.value)
}

case class RateLimitingPlan(id: RateLimitingPlanId, name: RateLimitingPlanName, operationLimitations: Seq[OperationLimitations])

case class RateLimitingPlanCreateRequest(name: RateLimitingPlanName, operationLimitations: OperationLimitationsType) {
  def operationLimitationsValue: Seq[OperationLimitations] = operationLimitations.value

  def nameValue: String = name.value
}

case class RateLimitingPlanResetRequest(id: RateLimitingPlanId, name: RateLimitingPlanName, operationLimitations: OperationLimitationsType) {
  def operationLimitationsValue: Seq[OperationLimitations] = operationLimitations.value

  def nameValue: String = name.value
}

case class UsernameToRateLimitingPlanId(username: Username, rateLimitingPlanId: RateLimitingPlanId)
