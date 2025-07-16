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

package com.linagora.tmail.rate.limiter.api.cassandra.dao

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, insertInto, selectFrom, update}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanId, UsernameToRateLimitingPlanId}
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.{RATE_LIMITING_PLAN_ID, TABLE_NAME, USER}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}

class CassandraRateLimitPlanUserDAO @Inject()(session: CqlSession) {
  private val executor = new CassandraAsyncExecutor(session)

  private val insertStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USER, bindMarker(USER))
    .value(RATE_LIMITING_PLAN_ID, bindMarker(RATE_LIMITING_PLAN_ID))
    .build())

  private val selectOnePlanIdStatement = session.prepare(selectFrom(TABLE_NAME).column(RATE_LIMITING_PLAN_ID)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  private val selectAllStatement = session.prepare(selectFrom(TABLE_NAME)
    .columns(USER, RATE_LIMITING_PLAN_ID)
    .build())

  private val clearPlanIdOfUser = session.prepare(update(TABLE_NAME)
    .setColumn(RATE_LIMITING_PLAN_ID, bindMarker(RATE_LIMITING_PLAN_ID))
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  def insertRecord(username: Username, rateLimitingPlanId: RateLimitingPlanId): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind()
      .setString(USER, username.asString)
      .setUuid(RATE_LIMITING_PLAN_ID, rateLimitingPlanId.value)))

  def getPlanId(username: Username): SMono[RateLimitingPlanId] =
    SMono.fromPublisher(executor.executeSingleRow(selectOnePlanIdStatement.bind()
        .setString(USER, username.asString))
      .filter(associatedPlanIdExists)
      .map(this.readPlanId))

  def getAllRecord: SFlux[UsernameToRateLimitingPlanId] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind())
      .filter(associatedPlanIdExists)
      .map(this.readRow))

  def clearPlanId(username: Username): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(clearPlanIdOfUser.bind()
      .set(USER, username.asString, TypeCodecs.TEXT)
      .setToNull(RATE_LIMITING_PLAN_ID)))

  private def readPlanId(row: Row): RateLimitingPlanId = RateLimitingPlanId(row.getUuid(RATE_LIMITING_PLAN_ID))

  private def readRow(row: Row): UsernameToRateLimitingPlanId = UsernameToRateLimitingPlanId(Username.of(row.getString(USER)),
    RateLimitingPlanId(row.getUuid(RATE_LIMITING_PLAN_ID)))

  private def associatedPlanIdExists(row: Row): Boolean =
    row.getUuid(RATE_LIMITING_PLAN_ID) != null
}
