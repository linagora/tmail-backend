package com.linagora.tmail.james.jmap.longlivedtoken

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, Row}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}
import javax.inject.Inject

object LongLivedTokenStoreTable {

  val TABLE_NAME: String = "long_lived_token"
  val USERNAME: String = "username"
  val SECRET_KEY: String = "secret_key"
  val DEVICE_ID: String = "device_id"
  val TOKEN_ID: String = "token_id"

  val module: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Holds long lived token for user in order to authen JMAP")
    .statement(statement => types => statement
      .withPartitionKey(USERNAME, DataTypes.TEXT)
      .withClusteringColumn(SECRET_KEY, DataTypes.UUID)
      .withColumn(TOKEN_ID, DataTypes.UUID)
      .withColumn(DEVICE_ID, DataTypes.TEXT))
    .build
}

class CassandraLongLivedTokenDAO @Inject()(session: CqlSession) {

  import LongLivedTokenStoreTable._

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USERNAME, bindMarker(USERNAME))
    .value(SECRET_KEY, bindMarker(SECRET_KEY))
    .value(DEVICE_ID, bindMarker(DEVICE_ID))
    .value(TOKEN_ID, bindMarker(TOKEN_ID))
    .build())

  private val listStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .build())

  private val selectStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .whereColumn(SECRET_KEY).isEqualTo(bindMarker(SECRET_KEY))
    .build())

  private val deleteStatement: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .whereColumn(SECRET_KEY).isEqualTo(bindMarker(SECRET_KEY))
    .build())

  def insert(username: Username, longLivedToken: LongLivedToken): SMono[LongLivedTokenId] =
    SMono.fromCallable(() => LongLivedTokenId.generate)
      .flatMap(longLivedTokenId =>
        SMono.fromPublisher(executor.executeVoid(insertStatement.bind()
          .setString(USERNAME, username.asString())
          .setUuid(SECRET_KEY, longLivedToken.secret.value)
          .setString(DEVICE_ID, longLivedToken.deviceId.value)
          .setUuid(TOKEN_ID, longLivedTokenId.value)))
          .`then`(SMono.just(longLivedTokenId)))

  def validate(username: Username, secret: LongLivedTokenSecret): SMono[LongLivedTokenFootPrint] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind()
      .setString(USERNAME, username.asString())
      .setUuid(SECRET_KEY, secret.value)))
      .map(row => readRow(row))

  def list(username: Username): SFlux[LongLivedTokenFootPrint] =
    SFlux.fromPublisher(executor.executeRows(listStatement.bind()
      .setString(USERNAME, username.asString())))
      .map(row => readRow(row))

  def delete(username: Username, longLivedTokenId: LongLivedTokenId): SMono[Unit] =
    lookupSecretKey(username, longLivedTokenId)
      .flatMap(secretKey =>
        SMono.fromPublisher(executor.executeVoid(deleteStatement.bind()
          .setString(USERNAME, username.asString())
          .setUuid(SECRET_KEY, secretKey.value))))
      .`then`()

  def lookupSecretKey(username: Username, longLivedTokenId: LongLivedTokenId): SMono[LongLivedTokenSecret] =
    SFlux.fromPublisher(executor.executeRows(listStatement.bind()
      .setString(USERNAME, username.asString())))
      .filter(row => row.getUuid(TOKEN_ID).equals(longLivedTokenId.value))
      .map(row => LongLivedTokenSecret(row.getUuid(SECRET_KEY)))
      .head

  private def readRow(row: Row): LongLivedTokenFootPrint =
    LongLivedTokenFootPrint(
      LongLivedTokenId(row.getUuid(TOKEN_ID)),
      DeviceId(row.getString(DEVICE_ID)))
}
