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

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.{InMemoryRateLimitingPlanRepository, RateLimitingPlanRepository, RateLimitingPlanUserRepository, RateLimitingPlanUsernameChangeTaskStep, RateLimitingRepository, RateLimitingUsernameChangeTaskStep}
import org.apache.james.user.api.UsernameChangeTaskStep

class MemoryRateLimitingModule() extends AbstractModule {

  override def configure(): Unit = {
    bind(classOf[MemoryRateLimitingPlanUserRepository]).in(Scopes.SINGLETON)
    bind(classOf[InMemoryRateLimitingPlanRepository]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[MemoryRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[InMemoryRateLimitingPlanRepository])

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingPlanUsernameChangeTaskStep])

    bind(classOf[MemoryRateLimitingRepository]).in(Scopes.SINGLETON)
    bind(classOf[RateLimitingRepository]).to(classOf[MemoryRateLimitingRepository])

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingUsernameChangeTaskStep])
  }
}
