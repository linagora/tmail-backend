package com.linagora.tmail.rate.limiter.cassandra

import com.datastax.driver.core.DataType.{bigint, text}
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, insertInto, select}
import com.datastax.driver.core.{DataType, PreparedStatement, Row, Session, TupleType, TupleValue}
import com.linagora.tmail.rate.limiter.api.{LimitTypes, OperationLimitations, RateLimitation, RateLimitingPlanCreateRequest, RateLimitingPlanId, RateLimitingPlanResetRequest}
import com.linagora.tmail.rate.limiter.cassandra.CassandraRateLimitPlanHeaderEntry.{RATE_LIMITATION_DURATION_INDEX, RATE_LIMITATION_NAME_INDEX, RATE_LIMITS_INDEX}
import com.linagora.tmail.rate.limiter.cassandra.CassandraRateLimitPlanTable.{OPERATION_LIMITATION_NAME, PLAN_ID, PLAN_NAME, RATE_LIMITATIONS, TABLE_NAME}
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import reactor.core.scala.publisher.{SFlux, SMono}

import java.time.Duration
import java.util
import java.util.UUID
import javax.inject.Inject
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

class CassandraRateLimitPlanDAO @Inject()(session: Session) {
  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  private val rateLimitationsTuple: TupleType = session.getCluster.getMetadata.newTupleType(text(), bigint(), DataType.map(text(), bigint()))

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(PLAN_ID, bindMarker(PLAN_ID))
    .value(PLAN_NAME, bindMarker(PLAN_NAME))
    .value(OPERATION_LIMITATION_NAME, bindMarker(OPERATION_LIMITATION_NAME))
    .value(RATE_LIMITATIONS, bindMarker(RATE_LIMITATIONS)))

  private val deleteStatement: PreparedStatement = session.prepare(QueryBuilder.delete().from(TABLE_NAME)
    .where(QueryBuilder.eq(PLAN_ID, bindMarker(PLAN_ID))))

  private val selectStatement: PreparedStatement = session.prepare(select.from(TABLE_NAME)
    .where(QueryBuilder.eq(PLAN_ID, bindMarker(PLAN_ID))))

  private val selectAllStatement: PreparedStatement = session.prepare(select.from(TABLE_NAME))

  def insert(insertEntry: RateLimitingPlanEntry): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement
      .bind.setUUID(PLAN_ID, insertEntry.planId)
      .setString(PLAN_NAME, insertEntry.planName)
      .setString(OPERATION_LIMITATION_NAME, insertEntry.operationLimitations.asString())
      .setList(RATE_LIMITATIONS, toTupleList(insertEntry.operationLimitations.rateLimitations()))))

  def delete(planId: RateLimitingPlanId): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind
      .setUUID(PLAN_ID, planId.value)))

  def list(planId: RateLimitingPlanId): SFlux[RateLimitingPlanEntry] =
    SFlux.fromPublisher(executor.executeRows(selectStatement
      .bind.setUUID(PLAN_ID, planId.value)))
      .map(readRow)

  def planExists(planId: RateLimitingPlanId): SMono[Boolean] =
    SMono.fromPublisher(executor.executeReturnExists(selectStatement
      .bind.setUUID(PLAN_ID, planId.value)))
      .map(_.booleanValue())

  def list(): SFlux[RateLimitingPlanEntry] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind))
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
      planId = row.getUUID(PLAN_ID),
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
