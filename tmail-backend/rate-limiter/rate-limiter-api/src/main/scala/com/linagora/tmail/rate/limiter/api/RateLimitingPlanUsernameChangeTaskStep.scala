package com.linagora.tmail.rate.limiter.api

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.apache.james.user.api.UsernameChangeTaskStep.StepName
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class RateLimitingPlanUsernameChangeTaskStep @Inject() (val repository: RateLimitingPlanUserRepository) extends UsernameChangeTaskStep {
  override def name(): StepName = new StepName("RateLimitingPlanUsernameChangeTaskStep")

  override def priority(): Int = 7

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] = {
    SMono(repository.getPlanByUser(newUsername))
      .onErrorResume {
        case _: RateLimitingPlanNotFoundException => migratePlan(oldUsername, newUsername)
      }
      .`then`(SMono(repository.revokePlan(oldUsername)))
      .`then`()
  }

  private def migratePlan(oldUsername: Username, newUsername: Username) =
    SMono(repository.getPlanByUser(oldUsername))
      .onErrorResume {
        case _: RateLimitingPlanNotFoundException => SMono.empty
      }
      .flatMap(planId => SMono(repository.applyPlan(newUsername, planId)))
}
