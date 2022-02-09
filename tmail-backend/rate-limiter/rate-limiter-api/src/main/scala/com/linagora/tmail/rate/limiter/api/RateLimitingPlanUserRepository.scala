package com.linagora.tmail.rate.limiter.api

import com.linagora.tmail.rate.limiter.model.RateLimitingPlanId
import org.apache.james.core.Username
import org.reactivestreams.Publisher

trait RateLimitingPlanUserRepository {
  def applyPlan(username: Username, planId: RateLimitingPlanId): Publisher[Unit]

  def revokePlan(username: Username): Publisher[Unit]

  def listUsers(planId: RateLimitingPlanId): Publisher[Username]

  def getPlanByUser(username: Username): Publisher[RateLimitingPlanId]
}
