package com.linagora.tmail.blob.blobid.list.postgres

import com.linagora.tmail.blob.blobid.list.postgres.PostgresBlobIdListModule.{BLOB_ID, TABLE_NAME}
import jakarta.inject.Inject
import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.blob.api.BlobId
import reactor.core.publisher.Mono
import reactor.core.scala.publisher.SMono

class PostgresBlobIdListDAO @Inject()(postgresExecutor: PostgresExecutor) {
  def insert(blobId: BlobId): SMono[Unit] =
    SMono.fromPublisher(postgresExecutor.executeVoid(dsl => Mono.from(dsl.insertInto(TABLE_NAME)
        .set(BLOB_ID, blobId.asString()))))
      .`then`

  def isStored(blobId: BlobId): SMono[java.lang.Boolean] =
    SMono.fromPublisher(postgresExecutor.executeExists(dsl => dsl.selectOne()
      .from(TABLE_NAME)
      .where(BLOB_ID.eq(blobId.asString()))))

  def remove(blobId: BlobId): SMono[Unit] =
    SMono.fromPublisher(postgresExecutor.executeVoid(dsl => Mono.from(dsl.deleteFrom(TABLE_NAME)
        .where(BLOB_ID.eq(blobId.asString())))))
      .`then`
}
