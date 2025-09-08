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

package com.linagora.tmail.rate.limiter.api.postgres.module

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.postgres.repository.PostgresRateLimitingPlanUserRepository
import com.linagora.tmail.rate.limiter.api.postgres.table.PostgresRateLimitPlanModule
import com.linagora.tmail.rate.limiter.api.postgres.{PostgresRateLimitingPlanRepository, PostgresRateLimitingRepository}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanRepository, RateLimitingPlanUserRepository, RateLimitingPlanUsernameChangeTaskStep, RateLimitingRepository}
import org.apache.james.backends.postgres.PostgresDataDefinition
import org.apache.james.user.api.UsernameChangeTaskStep

class PostgresRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[PostgresRateLimitingPlanUserRepository]).in(Scopes.SINGLETON)
    bind(classOf[PostgresRateLimitingPlanRepository]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[PostgresRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[PostgresRateLimitingPlanRepository])

    val postgresRateLimitingDataDefinitions = Multibinder.newSetBinder(binder, classOf[PostgresDataDefinition])
    postgresRateLimitingDataDefinitions.addBinding().toInstance(PostgresRateLimitPlanModule.MODULE)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingPlanUsernameChangeTaskStep])

    bind(classOf[PostgresRateLimitingRepository]).in(Scopes.SINGLETON)
    bind(classOf[RateLimitingRepository]).to(classOf[PostgresRateLimitingRepository])
  }
}
