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

import jakarta.inject.Inject
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
