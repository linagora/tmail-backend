package com.linagora.tmail.james.jmap.ticket

import com.datastax.driver.core.DataType.{text, uuid}
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, insertInto, select}
import com.datastax.driver.core.schemabuilder.Create
import com.datastax.driver.core.{PreparedStatement, Row, Session}
import com.linagora.tmail.james.jmap.longlivedtoken.{DeviceId, LongLivedToken, LongLivedTokenFootPrint, LongLivedTokenId, LongLivedTokenSecret}
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
    .statement((statement: Create) => statement
      .addPartitionKey(USERNAME, text)
      .addClusteringColumn(SECRET_KEY, uuid())
      .addColumn(TOKEN_ID, uuid())
      .addColumn(DEVICE_ID, text))
    .build
}

class CassandraLongLivedTokenDAO @Inject()(session: Session) {

  import LongLivedTokenStoreTable._

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USERNAME, bindMarker(USERNAME))
    .value(SECRET_KEY, bindMarker(SECRET_KEY))
    .value(DEVICE_ID, bindMarker(DEVICE_ID))
    .value(TOKEN_ID, bindMarker(TOKEN_ID)))

  private val listStatement: PreparedStatement = session.prepare(select.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME))))

  private val selectStatement: PreparedStatement = session.prepare(select.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME)))
    .and(QueryBuilder.eq(SECRET_KEY, bindMarker(SECRET_KEY))))

  private val deleteStatement: PreparedStatement = session.prepare(QueryBuilder.delete.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME)))
    .and(QueryBuilder.eq(SECRET_KEY, bindMarker(SECRET_KEY))))

  def insert(username: Username, longLivedToken: LongLivedToken): SMono[LongLivedTokenId] = {
    SMono.fromCallable(() => LongLivedTokenId.generate)
      .flatMap(longLivedTokenId =>
        SMono.fromPublisher(executor.executeVoid(insertStatement.bind
          .setString(USERNAME, username.asString())
          .setUUID(SECRET_KEY, longLivedToken.secret.value)
          .setString(DEVICE_ID, longLivedToken.deviceId.value)
          .setUUID(TOKEN_ID, longLivedTokenId.value)))
          .`then`(SMono.just(longLivedTokenId)))
  }

  def validate(username: Username, secret: LongLivedTokenSecret): SMono[LongLivedTokenFootPrint] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind
      .setString(USERNAME, username.asString())
      .setUUID(SECRET_KEY, secret.value)))
      .map(row => readRow(row))

  def list(username: Username): SFlux[LongLivedTokenFootPrint] =
    SFlux.fromPublisher(executor.executeRows(listStatement.bind
      .setString(USERNAME, username.asString())))
      .map(row => readRow(row))

  def delete(username: Username, longLivedTokenId: LongLivedTokenId): SMono[Unit] =
    lookupSecretKey(username, longLivedTokenId)
      .flatMap(secretKey =>
        SMono.fromPublisher(executor.executeVoid(deleteStatement.bind
          .setString(USERNAME, username.asString())
          .setUUID(SECRET_KEY, secretKey.value))))
      .`then`()

  def lookupSecretKey(username: Username, longLivedTokenId: LongLivedTokenId): SMono[LongLivedTokenSecret] =
    SFlux.fromPublisher(executor.executeRows(listStatement.bind
      .setString(USERNAME, username.asString())))
      .filter(row => row.getUUID(TOKEN_ID).equals(longLivedTokenId.value))
      .map(row => LongLivedTokenSecret(row.getUUID(SECRET_KEY)))
      .head

  private def readRow(row: Row): LongLivedTokenFootPrint =
    LongLivedTokenFootPrint(
      LongLivedTokenId(row.getUUID(TOKEN_ID)),
      DeviceId(row.getString(DEVICE_ID)))
}
