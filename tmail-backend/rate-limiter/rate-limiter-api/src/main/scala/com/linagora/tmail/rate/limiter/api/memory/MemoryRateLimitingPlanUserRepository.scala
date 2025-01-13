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

package com.linagora.tmail.rate.limiter.api.memory

import com.google.common.base.Preconditions
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanId, RateLimitingPlanNotFoundException, RateLimitingPlanUserRepository}
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.collection.concurrent.Map

class MemoryRateLimitingPlanUserRepository extends RateLimitingPlanUserRepository {
  val table: Map[Username, RateLimitingPlanId] = scala.collection.concurrent.TrieMap()

  override def applyPlan(username: Username, planId: RateLimitingPlanId): SMono[Unit] = {
    Preconditions.checkNotNull(username)
    Preconditions.checkNotNull(planId)
    SMono.fromCallable(() => table.put(username, planId)).`then`()
  }

  override def revokePlan(username: Username): SMono[Unit] = {
    Preconditions.checkNotNull(username)
    SMono.fromCallable(() => table.remove(username)).`then`()
  }

  override def listUsers(planId: RateLimitingPlanId): SFlux[Username] = {
    Preconditions.checkNotNull(planId)
    SFlux.fromIterable(table.filter(usernameToPlanId => usernameToPlanId._2.equals(planId)).keys)
  }

  override def getPlanByUser(username: Username): SMono[RateLimitingPlanId] = {
    Preconditions.checkNotNull(username)
    SMono.justOrEmpty(table.get(username))
      .switchIfEmpty(SMono.error(RateLimitingPlanNotFoundException()))
  }
}
