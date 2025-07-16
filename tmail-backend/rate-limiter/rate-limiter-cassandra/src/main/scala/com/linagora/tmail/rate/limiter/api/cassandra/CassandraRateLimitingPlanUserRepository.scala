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
    dao.clearPlanId(username).`then`()
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
