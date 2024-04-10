package com.linagora.tmail.rate.limiter.api.cassandra

import com.google.common.base.Preconditions
import com.linagora.tmail.rate.limiter.api.cassandra.dao.CassandraRateLimitPlanUserDAO
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanId, RateLimitingPlanNotFoundException, RateLimitingPlanUserRepository}
import jakarta.inject.Inject
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}

class CassandraRateLimitingPlanUserRepository @Inject()(dao: CassandraRateLimitPlanUserDAO) extends RateLimitingPlanUserRepository {
  override def applyPlan(username: Username, planId: RateLimitingPlanId): SMono[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(planId)
    dao.insertRecord(username, planId).`then`()
  }

  override def revokePlan(username: Username): SMono[Unit] = {
    Preconditions.checkNotNull(username)
    dao.deleteRecord(username).`then`()
  }

  override def listUsers(planId: RateLimitingPlanId): SFlux[Username] = {
    Preconditions.checkNotNull(planId)
    dao.getAllRecord
      .filter(_.rateLimitingPlanId.equals(planId))
      .map(_.username)
  }

  override def getPlanByUser(username: Username): SMono[RateLimitingPlanId] = {
    Preconditions.checkNotNull(username)
    dao.getPlanId(username)
      .switchIfEmpty(SMono.error(RateLimitingPlanNotFoundException()))
  }
}
