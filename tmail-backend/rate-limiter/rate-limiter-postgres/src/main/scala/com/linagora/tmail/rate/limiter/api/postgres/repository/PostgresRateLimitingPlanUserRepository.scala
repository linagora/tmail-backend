/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.rate.limiter.api.postgres.repository

import com.google.common.base.Preconditions
import com.linagora.tmail.rate.limiter.api.postgres.dao.PostgresRateLimitPlanUserDAO
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanId, RateLimitingPlanNotFoundException, RateLimitingPlanUserRepository}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

class PostgresRateLimitingPlanUserRepository @Inject()(dao: PostgresRateLimitPlanUserDAO) extends RateLimitingPlanUserRepository {

  override def applyPlan(username: Username, planId: RateLimitingPlanId): Publisher[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(planId)

    SMono(dao.insert(username, planId))
      .`then`()
  }

  override def revokePlan(username: Username): Publisher[Unit] = {
    Preconditions.checkNotNull(username)

    SMono(dao.clearPlan(username))
      .`then`()
  }

  override def listUsers(planId: RateLimitingPlanId): Publisher[Username] = {
    Preconditions.checkNotNull(planId)

    dao.getUsersByPlanId(planId)
  }

  override def getPlanByUser(username: Username): Publisher[RateLimitingPlanId] = {
    Preconditions.checkNotNull(username)

    dao.getPlanIdByUser(username)
      .switchIfEmpty(Mono.error(() => RateLimitingPlanNotFoundException()))
  }
}
