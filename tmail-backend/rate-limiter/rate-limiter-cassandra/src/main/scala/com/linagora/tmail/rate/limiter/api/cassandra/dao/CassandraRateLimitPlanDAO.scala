package com.linagora.tmail.rate.limiter.api.cassandra.dao

import java.time.Duration
import java.util
import java.util.UUID

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.{DataTypes, TupleType}
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, Row}
import com.datastax.oss.driver.api.core.data.TupleValue
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanHeaderEntry.{RATE_LIMITATION_DURATION_INDEX, RATE_LIMITATION_NAME_INDEX, RATE_LIMITS_INDEX}
import com.linagora.tmail.rate.limiter.api.cassandra.table.CassandraRateLimitPlanTable.{OPERATION_LIMITATION_NAME, PLAN_ID, PLAN_NAME, RATE_LIMITATIONS, TABLE_NAME}
import com.linagora.tmail.rate.limiter.api.{LimitTypes, OperationLimitations, RateLimitation, RateLimitingPlanCreateRequest, RateLimitingPlanId, RateLimitingPlanResetRequest}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object RateLimitingPlanEntry {
  def from(planId: RateLimitingPlanId, createRequest: RateLimitingPlanCreateRequest): Seq[RateLimitingPlanEntry] =
    createRequest.operationLimitations.value
      .map(operationLimitations => RateLimitingPlanEntry(
        planId = planId.value,
        planName = createRequest.name.value,
        operationLimitations = operationLimitations))

  def from(resetRequest: RateLimitingPlanResetRequest): Seq[RateLimitingPlanEntry] =
    resetRequest.operationLimitations.value
      .map(operationLimitations => RateLimitingPlanEntry(
        planId = resetRequest.id.value,
        planName = resetRequest.name.value,
        operationLimitations = operationLimitations))
}

case class RateLimitingPlanEntry(planId: UUID,
                                 planName: String,
                                 operationLimitations: OperationLimitations)

class CassandraRateLimitPlanDAO @Inject()(session: CqlSession) {
  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  private val rateLimitationsTuple: TupleType = DataTypes.tupleOf(DataTypes.TEXT, DataTypes.BIGINT, DataTypes.frozenMapOf(DataTypes.TEXT, DataTypes.BIGINT))

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(PLAN_ID, bindMarker(PLAN_ID))
    .value(PLAN_NAME, bindMarker(PLAN_NAME))
    .value(OPERATION_LIMITATION_NAME, bindMarker(OPERATION_LIMITATION_NAME))
    .value(RATE_LIMITATIONS, bindMarker(RATE_LIMITATIONS))
    .build())

  private val deleteStatement: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(PLAN_ID).isEqualTo(bindMarker(PLAN_ID))
    .build())

  private val selectStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(PLAN_ID).isEqualTo(bindMarker(PLAN_ID))
    .build())

  private val selectAllStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME).all().build())

  def insert(insertEntry: RateLimitingPlanEntry): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement
      .bind().setUuid(PLAN_ID, insertEntry.planId)
      .setString(PLAN_NAME, insertEntry.planName)
      .setString(OPERATION_LIMITATION_NAME, insertEntry.operationLimitations.asString())
      .setList(RATE_LIMITATIONS, toTupleList(insertEntry.operationLimitations.rateLimitations()), classOf[TupleValue])))

  def delete(planId: RateLimitingPlanId): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind()
      .setUuid(PLAN_ID, planId.value)))

  def list(planId: RateLimitingPlanId): SFlux[RateLimitingPlanEntry] =
    SFlux.fromPublisher(executor.executeRows(selectStatement
      .bind().setUuid(PLAN_ID, planId.value)))
      .map(readRow)

  def planExists(planId: RateLimitingPlanId): SMono[Boolean] =
    SMono.fromPublisher(executor.executeReturnExists(selectStatement
      .bind().setUuid(PLAN_ID, planId.value)))
      .map(_.booleanValue())

  def list(): SFlux[RateLimitingPlanEntry] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind()))
      .map(readRow)

  private def readRow(row: Row): RateLimitingPlanEntry = {
    val rateLimitations: Seq[RateLimitation] = row.getList(RATE_LIMITATIONS, classOf[TupleValue]).asScala
      .map(tupleData => {
        val limits: Map[String, Long] = tupleData.getMap(RATE_LIMITS_INDEX, classOf[String], classOf[java.lang.Long])
          .asScala.map(pair => pair._1 -> pair._2.toLong)
          .toMap
        RateLimitation(
          name = tupleData.getString(RATE_LIMITATION_NAME_INDEX),
          period = Duration.ofMillis(tupleData.getLong(RATE_LIMITATION_DURATION_INDEX)),
          limits = LimitTypes.from(limits))
      }).toSeq

    RateLimitingPlanEntry(
      planId = row.getUuid(PLAN_ID),
      planName = row.getString(PLAN_NAME),
      operationLimitations = OperationLimitations.liftOrThrow(row.getString(OPERATION_LIMITATION_NAME), rateLimitations))
  }

  private def toTupleList(rateLimitations: Seq[RateLimitation]): java.util.List[TupleValue] =
    rateLimitations.map(rateLimitation => {
      val limits: util.Map[String, Long] = rateLimitation.limits.value
        .map(limitType => limitType.asString() -> limitType.allowedQuantity().value)
        .toMap.asJava
      rateLimitationsTuple.newValue(rateLimitation.name, rateLimitation.period.toMillis, limits)
    }).asJava

}
