package com.linagora.tmail.rate.limiter.api.cassandra.dao

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanUserTable.{PLAN_ID, TABLE_NAME, USERNAME}
import com.linagora.tmail.rate.limiter.api.{RateLimitingPlanId, UsernameToRateLimitingPlanId}
import javax.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}

class CassandraRateLimitPlanUserDAO @Inject()(session: CqlSession) {
  private val executor = new CassandraAsyncExecutor(session)

  private val insertStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USERNAME, bindMarker(USERNAME))
    .value(PLAN_ID, bindMarker(PLAN_ID))
    .build())

  private val selectOnePlanIdStatement = session.prepare(selectFrom(TABLE_NAME).column(PLAN_ID)
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .build())

  private val selectAllStatement = session.prepare(selectFrom(TABLE_NAME).all().build())

  private val deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .build())

  def insertRecord(username: Username, rateLimitingPlanId: RateLimitingPlanId): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind().setString(USERNAME, username.asString)
      .setUuid(PLAN_ID, rateLimitingPlanId.value)))

  def getPlanId(username: Username): SMono[RateLimitingPlanId] =
    SMono.fromPublisher(executor.executeSingleRow(selectOnePlanIdStatement.bind().setString(USERNAME, username.asString))
      .map(this.readPlanId))

  def getAllRecord: SFlux[UsernameToRateLimitingPlanId] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind())
      .map(this.readRow))

  def deleteRecord(username: Username): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind().setString(USERNAME, username.asString)))

  private def readPlanId(row: Row): RateLimitingPlanId = RateLimitingPlanId(row.getUuid(PLAN_ID))

  private def readRow(row: Row): UsernameToRateLimitingPlanId = UsernameToRateLimitingPlanId(Username.of(row.getString(USERNAME)),
    RateLimitingPlanId(row.getUuid(PLAN_ID)))
}
