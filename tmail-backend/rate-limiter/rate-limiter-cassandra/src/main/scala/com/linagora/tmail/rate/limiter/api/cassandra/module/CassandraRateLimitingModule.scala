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

package com.linagora.tmail.rate.limiter.api.cassandra.module

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.rate.limiter.api.cassandra.dao.{CassandraRateLimitPlanDAO, CassandraRateLimitPlanUserDAO}
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanTable
import com.linagora.tmail.rate.limiter.api.cassandra.{CassandraRateLimitingPlanRepository, CassandraRateLimitingPlanUserRepository, CassandraRateLimitingRepository}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanRepository, RateLimitingPlanUserRepository, RateLimitingPlanUsernameChangeTaskStep, RateLimitingRepository, RateLimitingUsernameChangeTaskStep}
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.user.api.UsernameChangeTaskStep

class CassandraRateLimitingModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraRateLimitPlanDAO]).in(Scopes.SINGLETON)
    bind(classOf[CassandraRateLimitPlanUserDAO]).in(Scopes.SINGLETON)

    bind(classOf[RateLimitingPlanUserRepository]).to(classOf[CassandraRateLimitingPlanUserRepository])
    bind(classOf[RateLimitingPlanRepository]).to(classOf[CassandraRateLimitingPlanRepository])

    val multibinder = Multibinder.newSetBinder(binder, classOf[CassandraDataDefinition])
    multibinder.addBinding().toInstance(CassandraRateLimitPlanTable.MODULE)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingPlanUsernameChangeTaskStep])

    bind(classOf[CassandraRateLimitingRepository]).in(Scopes.SINGLETON)
    bind(classOf[RateLimitingRepository]).to(classOf[CassandraRateLimitingRepository])

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[RateLimitingUsernameChangeTaskStep])
  }
}
