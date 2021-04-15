package com.linagora.openpaas.encrypted.cassandra

import java.nio.ByteBuffer

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, delete, insertInto, select}
import com.datastax.driver.core.{Row, Session}
import com.linagora.openpaas.encrypted.cassandra.table.KeyStoreTable.{ID, KEY, TABLE_NAME, USERNAME}
import com.linagora.openpaas.encrypted.{KeyId, PublicKey}
import javax.inject.Inject
import org.apache.james.backends.cassandra.init.CassandraTypesProvider
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}

class CassandraKeystoreDAO @Inject()(session: Session, cassandraTypesProvider: CassandraTypesProvider) {
  private val executor = new CassandraAsyncExecutor(session)

  private val insertStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USERNAME, bindMarker(USERNAME))
    .value(ID, bindMarker(ID))
    .value(KEY, bindMarker(KEY)))

  private val selectStatement = session.prepare(select.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME)))
    .and(QueryBuilder.eq(ID, bindMarker(ID))))

  private val selectAllStatement = session.prepare(select.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME))))

  private val deleteStatement = session.prepare(delete.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME)))
    .and(QueryBuilder.eq(ID, bindMarker(ID))))

  private val deleteAllStatement = session.prepare(delete.from(TABLE_NAME)
    .where(QueryBuilder.eq(USERNAME, bindMarker(USERNAME))))

  def insert(username: Username, key: PublicKey): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind.setString(USERNAME, username.asString)
      .setString(ID, key.id.value)
      .setBytes(KEY, ByteBuffer.wrap(key.payload))))

  def getKey(username: Username, id: KeyId): SMono[PublicKey] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind
      .setString(USERNAME, username.asString)
      .setString(ID, id.value))
      .map(this.readRow))

  def getAllKeys(username: Username): SFlux[PublicKey] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind
      .setString(USERNAME, username.asString))
      .map(this.readRow))

  def deleteKey(username: Username, id: KeyId): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind
      .setString(USERNAME, username.asString)
      .setString(ID, id.value)))

  def deleteAllKeys(username: Username): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteAllStatement.bind
      .setString(USERNAME, username.asString)))

  private def readRow(row: Row) = PublicKey(new KeyId(row.getString(ID)), row.getBytes(KEY).array)
}
