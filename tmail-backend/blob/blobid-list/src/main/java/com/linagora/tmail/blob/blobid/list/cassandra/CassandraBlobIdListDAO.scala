package com.linagora.tmail.blob.blobid.list.cassandra

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.linagora.tmail.blob.blobid.list.cassandra.BlobIdListTable._
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.blob.api.BlobId
import reactor.core.scala.publisher.SMono

class CassandraBlobIdListDAO @Inject()(session: CqlSession) {
  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement =
    session.prepare(insertInto(TABLE_NAME)
      .value(BLOB_ID, bindMarker(BLOB_ID))
      .build())

  private val selectStatement: PreparedStatement =
    session.prepare(selectFrom(TABLE_NAME)
      .all()
      .whereColumn(BLOB_ID).isEqualTo(bindMarker(BLOB_ID))
      .build())

  private val deleteStatement: PreparedStatement =
    session.prepare(deleteFrom(TABLE_NAME)
      .whereColumn(BLOB_ID).isEqualTo(bindMarker(BLOB_ID))
      .build())

  def insert(blobId: BlobId): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind().setString(BLOB_ID, blobId.asString)))
      .`then`

  def isStored(blobId: BlobId): SMono[java.lang.Boolean] =
    SMono.fromPublisher(executor.executeReturnExists(selectStatement.bind()
      .setString(BLOB_ID, blobId.asString)))
      .map(_.booleanValue())

  def remove(blobId: BlobId): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind()
      .setString(BLOB_ID, blobId.asString)))
      .`then`

}
