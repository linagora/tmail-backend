package com.linagora.tmail.rate.limiter.api

import com.linagora.tmail.rate.limiter.api.LimitTypes.LimitTypes
import eu.timepit.refined
import eu.timepit.refined.api.Refined
import eu.timepit.refined.collection.NonEmpty
import org.apache.james.rate.limiter.api.AllowedQuantity.AllowedQuantity

import java.time.Duration
import java.util.UUID
import scala.util.Try

object LimitTypes {
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
}

sealed trait LimitType {
  def asString(): String

  def allowedQuantity(): AllowedQuantity
}

case class Count(quantity: AllowedQuantity) extends LimitType {
  override def asString(): String = "count"

  override def allowedQuantity(): AllowedQuantity = quantity
}

case class Size(quantity: AllowedQuantity) extends LimitType {
  override def asString(): String = "size"

  override def allowedQuantity(): AllowedQuantity = quantity
}

case class RateLimitation(name: String, period: Duration, limits: LimitTypes)

object RateLimitingPlanId {
  def generate: RateLimitingPlanId = RateLimitingPlanId(UUID.randomUUID())

  def from(value: String): Try[RateLimitingPlanId] = Try(RateLimitingPlanId(UUID.fromString(value)))
}

case class RateLimitingPlanId(value: UUID)

case class RateLimitingPlanName(value: String)


sealed trait OperationLimitations {
  def asString(): String

  def rateLimitations(): Seq[RateLimitation]
}

case class TransitLimitations(value: Seq[RateLimitation]) extends OperationLimitations {
  override def asString(): String = "TransitLimitations"

  override def rateLimitations(): Seq[RateLimitation] = value
}

case class RelayLimitations(value: Seq[RateLimitation]) extends OperationLimitations {
  override def asString(): String = "RelayLimitations"

  override def rateLimitations(): Seq[RateLimitation] = value
}

case class DeliveryLimitations(value: Seq[RateLimitation]) extends OperationLimitations {
  override def asString(): String = "DeliveryLimitations"

  override def rateLimitations(): Seq[RateLimitation] = value
}

object RateLimitingPlan {
  def from(createRequest: RateLimitingPlanCreateRequest): RateLimitingPlan =
    RateLimitingPlan(id = RateLimitingPlanId.generate, name = createRequest.name, operationLimitations = createRequest.operationLimitations)

  def update(origin: RateLimitingPlan, resetRequest: RateLimitingPlanResetRequest): RateLimitingPlan =
    origin.copy(name = resetRequest.name, operationLimitations = resetRequest.operationLimitations)
}

case class RateLimitingPlan(id: RateLimitingPlanId, name: RateLimitingPlanName, operationLimitations: Seq[OperationLimitations])

case class RateLimitingPlanCreateRequest(name: RateLimitingPlanName, operationLimitations: Seq[OperationLimitations])

case class RateLimitingPlanResetRequest(id: RateLimitingPlanId, name: RateLimitingPlanName, operationLimitations: Seq[OperationLimitations])

