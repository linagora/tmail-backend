package com.linagora.tmail.rate.limiter.model

import java.util.UUID

import org.apache.james.core.Username

case class RateLimitingPlanId(value: UUID)
case class RateLimitationPlanNotFoundException(username: Username) extends RuntimeException