package com.linagora.tmail.mailets

import com.linagora.tmail.rate.limiter.api.LimitTypes.{COUNT, SIZE}
import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId
import eu.timepit.refined.auto._
import org.apache.james.core.Username
import org.apache.james.rate.limiter.api.Increment.Increment
import org.apache.james.rate.limiter.api.{AcceptableRate, Increment, RateLimiter, RateLimitingKey, RateLimitingResult}
import org.apache.james.transport.mailets.KeyPrefix
import org.apache.mailet.Mail
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

case class TmailPlanRateLimiter(rateLimiter: RateLimiter,
                                keyPrefix: Option[KeyPrefix] = None,
                                limitTypeName: String,
                                planId: RateLimitingPlanId,
                                operationLimitationName: String) {

  def rateLimit(username: Username, mail: Mail): Publisher[RateLimitingResult] =
    LimitTypeUtils.extractQuantity(mail, limitTypeName)
      .map(increment => rateLimiter.rateLimit(
        key = RateLimitingPlanKey(
          keyPrefix,
          limitTypeName,
          planId,
          operationLimitationName,
          username),
        increaseQuantity = increment))
      .getOrElse(SMono.just[RateLimitingResult](AcceptableRate))
}

case class RateLimitingPlanKey(keyPrefix: Option[KeyPrefix],
                               limitTypeName: String,
                               planId: RateLimitingPlanId,
                               operationLimitationName: String,
                               username: Username) extends RateLimitingKey {
  override def asString(): String = s"${
    keyPrefix.map(prefix => prefix.value + "_")
      .getOrElse("")
  }${planId.value.toString}_${operationLimitationName}_${limitTypeName}_${username.asString()}"
}

object LimitTypeUtils {
  def extractQuantity(mail: Mail, limitTypeName: String): Option[Increment] =
    limitTypeName match {
      case COUNT => Some(1)
      case SIZE => Some(Increment.liftOrThrow(mail.getMessageSize.toInt))
    }
}
