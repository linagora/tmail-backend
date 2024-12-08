package com.linagora.tmail.james.jmap.label

import com.datastax.oss.driver.api.core.`type`.DataTypes.{TEXT, TIMEUUID, frozenSetOf}
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.`type`.codec.{TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, Row}
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder.{ASC, DESC}
import com.datastax.oss.driver.api.core.{CqlIdentifier, CqlSession}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, insertInto, selectFrom}
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder.RowsPerPartition.rows
import com.linagora.tmail.james.jmap.model.{LabelChange, LabelId}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.backends.cassandra.utils.CassandraConstants.DEFAULT_CACHED_ROW_PER_PARTITION
import org.apache.james.jmap.api.change.State
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.jmap.core.Id
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._
import scala.jdk.StreamConverters._

object CassandraLabelChangeTable {
  val TABLE_NAME = "label_change"
  val ACCOUNT_ID: CqlIdentifier = CqlIdentifier.fromCql("account_id")
  val STATE: CqlIdentifier = CqlIdentifier.fromCql("state")
  val CREATED: CqlIdentifier = CqlIdentifier.fromCql("created")
  val UPDATED: CqlIdentifier = CqlIdentifier.fromCql("updated")
  val DESTROYED: CqlIdentifier = CqlIdentifier.fromCql("destroyed")

  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Hold JMAP label changes")
    .options(options => options
      .withClusteringOrder(STATE, ASC)
      .withCaching(true, rows(DEFAULT_CACHED_ROW_PER_PARTITION)))
    .statement(statement => _ => statement
      .withPartitionKey(ACCOUNT_ID, TEXT)
      .withClusteringColumn(STATE, TIMEUUID)
      .withColumn(CREATED, frozenSetOf(TEXT))
      .withColumn(UPDATED, frozenSetOf(TEXT))
      .withColumn(DESTROYED, frozenSetOf(TEXT)))
    .build

  val SET_OF_STRING_CODEC: TypeCodec[java.util.Set[String]] = CodecRegistry.DEFAULT.codecFor(frozenSetOf(TEXT))
  val TTL_FOR_ROW: CqlIdentifier = CqlIdentifier.fromCql("ttl")
}

class CassandraLabelChangeDAO @Inject()(session: CqlSession, labelChangesConfiguration: CassandraLabelChangesConfiguration) {
  import CassandraLabelChangeTable._

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)
  private val timeToLive: Int = Math.toIntExact(labelChangesConfiguration.labelChangeTtl.getSeconds)

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(ACCOUNT_ID, bindMarker(ACCOUNT_ID))
    .value(STATE, bindMarker(STATE))
    .value(CREATED, bindMarker(CREATED))
    .value(UPDATED, bindMarker(UPDATED))
    .value(DESTROYED, bindMarker(DESTROYED))
    .usingTtl(bindMarker(TTL_FOR_ROW))
    .build())

  private val selectAllStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
    .orderBy(STATE, ASC)
    .build())

  private val selectFromStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
    .whereColumn(STATE).isGreaterThanOrEqualTo(bindMarker(STATE))
    .orderBy(STATE, ASC)
    .build())

  private val selectLatestStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(ACCOUNT_ID).isEqualTo(bindMarker(ACCOUNT_ID))
    .orderBy(STATE, DESC)
    .limit(1)
    .build())

  def insert(labelChange: LabelChange): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind()
      .set(ACCOUNT_ID, labelChange.getAccountId.getIdentifier, TypeCodecs.TEXT)
      .setUuid(STATE, labelChange.state.getValue)
      .set(CREATED, toSetOfString(labelChange.created), SET_OF_STRING_CODEC)
      .set(UPDATED, toSetOfString(labelChange.updated), SET_OF_STRING_CODEC)
      .set(DESTROYED, toSetOfString(labelChange.destroyed), SET_OF_STRING_CODEC)
      .setInt(TTL_FOR_ROW, timeToLive)))

  def selectAllChanges(accountId: AccountId): SFlux[LabelChange] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind()
      .set(ACCOUNT_ID, accountId.getIdentifier, TypeCodecs.TEXT))
      .map(toLabelChange(_, accountId)))

  def selectChangesSince(accountId: AccountId, state: State): SFlux[LabelChange] =
    SFlux.fromPublisher(executor.executeRows(selectFromStatement.bind()
      .set(ACCOUNT_ID, accountId.getIdentifier, TypeCodecs.TEXT)
      .setUuid(STATE, state.getValue))
      .map(toLabelChange(_, accountId)))

  def selectLatestState(accountId: AccountId): SMono[State] =
    SMono.fromPublisher(executor.executeSingleRow(selectLatestStatement.bind()
      .set(ACCOUNT_ID, accountId.getIdentifier, TypeCodecs.TEXT))
      .map(toLabelChange(_, accountId)))
      .map(_.state)

  private def toLabelChange(row: Row, accountId: AccountId): LabelChange =
    LabelChange(accountId = accountId,
      state = State.of(row.getUuid(STATE)),
      created = toSetOfLabelId(row.get(CREATED, SET_OF_STRING_CODEC)),
      updated = toSetOfLabelId(row.get(UPDATED, SET_OF_STRING_CODEC)),
      destroyed = toSetOfLabelId(row.get(DESTROYED, SET_OF_STRING_CODEC)))

  private def toSetOfString(ids: Set[LabelId]): java.util.Set[String] =
    ids.map(_.serialize)
      .asJava

  private def toSetOfLabelId(ids: java.util.Set[String]): Set[LabelId] =
    ids.stream()
      .toScala(LazyList)
      .map(labelIdString => LabelId(Id.validate(labelIdString).toOption.get))
      .toSet
}
