package com.linagora.tmail.blob.blobid.list.cassandra

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, delete, insertInto, select}
import com.datastax.driver.core.{PreparedStatement, Session}
import com.linagora.tmail.blob.blobid.list.cassandra.BlobIdListTable._
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.blob.api.BlobId
import reactor.core.scala.publisher.SMono

import javax.inject.Inject

class CassandraBlobIdListDAO @Inject()(session: Session) {
  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement = {
    session.prepare(insertInto(TABLE_NAME)
      .value(BLOB_ID, bindMarker(BLOB_ID)))
  }

  private val selectStatement: PreparedStatement =
    session.prepare(select.from(TABLE_NAME)
      .where(QueryBuilder.eq(BLOB_ID, bindMarker(BLOB_ID))))

  private val deleteStatement: PreparedStatement =
    session.prepare(delete.from(TABLE_NAME)
      .where(QueryBuilder.eq(BLOB_ID, bindMarker(BLOB_ID))))

  def insert(blobId: BlobId): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind.setString(BLOB_ID, blobId.asString)))
      .`then`

  def isStored(blobId: BlobId): SMono[java.lang.Boolean] =
    SMono.fromPublisher(executor.executeReturnExists(selectStatement.bind
      .setString(BLOB_ID, blobId.asString)))
      .map(_.booleanValue())

  def remove(blobId: BlobId): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind
      .setString(BLOB_ID, blobId.asString)))
      .`then`

}
