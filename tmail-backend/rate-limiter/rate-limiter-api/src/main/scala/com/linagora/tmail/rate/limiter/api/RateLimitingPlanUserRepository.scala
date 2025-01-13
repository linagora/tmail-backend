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

package com.linagora.tmail.rate.limiter.api

import org.apache.james.core.Username
import org.reactivestreams.Publisher

trait RateLimitingPlanUserRepository {
  def applyPlan(username: Username, planId: RateLimitingPlanId): Publisher[Unit]

  def revokePlan(username: Username): Publisher[Unit]

  def listUsers(planId: RateLimitingPlanId): Publisher[Username]

  def getPlanByUser(username: Username): Publisher[RateLimitingPlanId]
}
