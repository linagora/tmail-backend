package com.linagora.tmail.rate.limiter.api.postgres.repository

import com.google.common.base.Preconditions
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitPlanUserDAO
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanId, RateLimitingPlanNotFoundException, RateLimitingPlanUserRepository}
import javax.inject.Inject
import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.core.Username
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

class PostgresRateLimitingPlanUserRepository @Inject()(executorFactory: PostgresExecutor.Factory) extends RateLimitingPlanUserRepository {
  private val daoWithoutRLS: PostgresRateLimitPlanUserDAO =
    PostgresRateLimitPlanUserDAO(executorFactory.create())

  override def applyPlan(username: Username, planId: RateLimitingPlanId): Publisher[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(planId)

    SMono(dao(username).insert(username, planId))
      .`then`()
  }

  override def revokePlan(username: Username): Publisher[Unit] = {
    Preconditions.checkNotNull(username)

    SMono(dao(username).delete(username))
      .`then`()
  }

  override def listUsers(planId: RateLimitingPlanId): Publisher[Username] = {
    Preconditions.checkNotNull(planId)

    daoWithoutRLS.getUsersByPlanId(planId)
  }

  override def getPlanByUser(username: Username): Publisher[RateLimitingPlanId] = {
    Preconditions.checkNotNull(username)

    dao(username).getPlanIdByUser(username)
      .switchIfEmpty(Mono.error(() => RateLimitingPlanNotFoundException()))
  }

  private def dao(username: Username) =
    PostgresRateLimitPlanUserDAO(executorFactory.create(username.getDomainPart))
}
