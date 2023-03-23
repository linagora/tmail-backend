package com.linagora.tmail.encrypted.cassandra

import java.nio.ByteBuffer

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.Row
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.linagora.tmail.encrypted.cassandra.table.KeyStoreTable.{ID, KEY, TABLE_NAME, USERNAME}
import com.linagora.tmail.encrypted.{KeyId, PublicKey}
import javax.inject.Inject
import org.apache.james.backends.cassandra.init.CassandraTypesProvider
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import reactor.core.scala.publisher.{SFlux, SMono}

class CassandraKeystoreDAO @Inject()(session: CqlSession) {
  private val executor = new CassandraAsyncExecutor(session)

  private val insertStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USERNAME, bindMarker(USERNAME))
    .value(ID, bindMarker(ID))
    .value(KEY, bindMarker(KEY))
    .build())

  private val selectStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .whereColumn(ID).isEqualTo(bindMarker(ID))
    .build())

  private val selectAllStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .build())

  private val deleteStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .whereColumn(ID).isEqualTo(bindMarker(ID))
    .build())

  private val deleteAllStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USERNAME).isEqualTo(bindMarker(USERNAME))
    .build())

  def insert(username: Username, key: PublicKey): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind().setString(USERNAME, username.asString)
      .setString(ID, key.id.value)
      .setByteBuffer(KEY, ByteBuffer.wrap(key.key))))

  def getKey(username: Username, id: KeyId): SMono[PublicKey] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind()
      .setString(USERNAME, username.asString)
      .setString(ID, id.value))
      .map(this.readRow))

  def getAllKeys(username: Username): SFlux[PublicKey] =
    SFlux.fromPublisher(executor.executeRows(selectAllStatement.bind()
      .setString(USERNAME, username.asString))
      .map(this.readRow))

  def deleteKey(username: Username, id: KeyId): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind()
      .setString(USERNAME, username.asString)
      .setString(ID, id.value)))

  def deleteAllKeys(username: Username): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteAllStatement.bind()
      .setString(USERNAME, username.asString)))

  private def readRow(row: Row) = PublicKey(new KeyId(row.getString(ID)), row.getByteBuffer(KEY).array)
}
