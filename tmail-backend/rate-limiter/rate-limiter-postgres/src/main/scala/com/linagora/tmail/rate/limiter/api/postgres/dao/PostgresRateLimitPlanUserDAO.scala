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

package com.linagora.tmail.rate.limiter.api.postgres.dao

import java.util.UUID

import com.linagora.tmail.rate.limiter.api.RateLimitingPlanId
import com.linagora.tmail.user.postgres.TMailPostgresUserDataDefinition.PostgresUserTable.{RATE_LIMITING_PLAN_ID, TABLE_NAME, USERNAME}
import jakarta.inject.Inject
import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.core.Username
import reactor.core.publisher.{Flux, Mono}

class PostgresRateLimitPlanUserDAO @Inject()(postgresExecutor: PostgresExecutor) {
  def insert(username: Username, rateLimitingPlanId: RateLimitingPlanId): Mono[Void] =
    postgresExecutor.executeVoid(dsl => Mono.from(dsl.insertInto(TABLE_NAME)
      .set(USERNAME, username.asString())
      .set(RATE_LIMITING_PLAN_ID, rateLimitingPlanId.value)
      .onConflict(USERNAME)
      .doUpdate()
      .set(RATE_LIMITING_PLAN_ID, rateLimitingPlanId.value)))

  def getPlanIdByUser(username: Username): Mono[RateLimitingPlanId] =
    postgresExecutor.executeRow(dsl => Mono.from(dsl.select(RATE_LIMITING_PLAN_ID)
      .from(TABLE_NAME)
      .where(USERNAME.eq(username.asString()))))
      .filter(_.get(RATE_LIMITING_PLAN_ID) != null)
      .map(record => RateLimitingPlanId(record.get(RATE_LIMITING_PLAN_ID)))

  def getUsersByPlanId(planId: RateLimitingPlanId): Flux[Username] =
    postgresExecutor.executeRows(dsl => Flux.from(dsl.select(USERNAME)
      .from(TABLE_NAME)
      .where(RATE_LIMITING_PLAN_ID.eq(planId.value))))
      .filter(_.get(USERNAME) != null)
      .map(record => Username.of(record.get(USERNAME)))

  def clearPlan(username: Username): Mono[Void] =
    postgresExecutor.executeVoid(dsl => Mono.from(dsl.update(TABLE_NAME)
      .set(RATE_LIMITING_PLAN_ID, null.asInstanceOf[UUID])
      .where(USERNAME.eq(username.asString()))))
}
