package com.linagora.tmail.encrypted.cassandra

import com.datastax.driver.core.DataType.{cint, frozenMap, text}
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.datastax.driver.core.querybuilder.QueryBuilder.{bindMarker, insertInto, select, delete => deleteBuilder}
import com.datastax.driver.core.{CodecRegistry, PreparedStatement, Row, Session, TypeCodec, TypeTokens}
import com.linagora.tmail.encrypted.cassandra.table.EncryptedEmailTable.{ENCRYPTED_ATTACHMENT_METADATA, ENCRYPTED_HTML, ENCRYPTED_PREVIEW, HAS_ATTACHMENT, MESSAGE_ID, POSITION_BLOB_ID_MAPPING, TABLE_NAME}
import com.linagora.tmail.encrypted.{EncryptedAttachmentMetadata, EncryptedEmailDetailedView, EncryptedHtml, EncryptedPreview}
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.blob.api.BlobId
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId
import reactor.core.scala.publisher.{SFlux, SMono}

import java.util
import javax.inject.Inject
import scala.jdk.CollectionConverters._

class CassandraEncryptedEmailDAO @Inject()(session: Session, blobIdFactory: BlobId.Factory) {
  private val MAP_OF_POSITION_BLOBID_CODEC: TypeCodec[util.Map[java.lang.Integer, String]] =
    CodecRegistry.DEFAULT_INSTANCE.codecFor(frozenMap(cint(), text),
      TypeTokens.mapOf(classOf[java.lang.Integer], classOf[String]))

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
    .value(ENCRYPTED_PREVIEW, bindMarker(ENCRYPTED_PREVIEW))
    .value(ENCRYPTED_HTML, bindMarker(ENCRYPTED_HTML))
    .value(HAS_ATTACHMENT, bindMarker(HAS_ATTACHMENT))
    .value(ENCRYPTED_ATTACHMENT_METADATA, bindMarker(ENCRYPTED_ATTACHMENT_METADATA))
    .value(POSITION_BLOB_ID_MAPPING, bindMarker(POSITION_BLOB_ID_MAPPING)))

  private val selectStatement: PreparedStatement = session.prepare(select.from(TABLE_NAME)
    .where(QueryBuilder.eq(MESSAGE_ID, bindMarker(MESSAGE_ID))))

  private val deleteStatement: PreparedStatement = session.prepare(deleteBuilder.from(TABLE_NAME)
    .where(QueryBuilder.eq(MESSAGE_ID, bindMarker(MESSAGE_ID))))

  def insert(cassandraMessageId: CassandraMessageId, encryptedEmailDetailed: EncryptedEmailDetailedView, positionBlobIdMapping: Map[Int, BlobId]): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind
      .setUUID(MESSAGE_ID, cassandraMessageId.get())
      .setString(ENCRYPTED_PREVIEW, encryptedEmailDetailed.encryptedPreview.value)
      .setString(ENCRYPTED_HTML, encryptedEmailDetailed.encryptedHtml.value)
      .setBool(HAS_ATTACHMENT, encryptedEmailDetailed.hasAttachment)
      .setString(ENCRYPTED_ATTACHMENT_METADATA, encryptedEmailDetailed.encryptedAttachmentMetadata
        .map(metadata => metadata.value)
        .orNull)
      .setMap(POSITION_BLOB_ID_MAPPING, positionBlobIdMapping
        .map(mapping => mapping._1 -> mapping._2.asString())
        .asJava)))
      .`then`()

  def get(cassandraMessageId: CassandraMessageId): SMono[EncryptedEmailDetailedView] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind
      .setUUID(MESSAGE_ID, cassandraMessageId.get())))
      .map(row => readRow(cassandraMessageId, row))

  def delete(cassandraMessageId: CassandraMessageId): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind
      .setUUID(MESSAGE_ID, cassandraMessageId.get())))
      .`then`()

  def getBlobId(cassandraMessageId: CassandraMessageId, position: Int): SMono[BlobId] =
    getMapOfPositionBlobId(cassandraMessageId)
      .map(positionBlobIdMapping => positionBlobIdMapping.get(position))
      .map(blobIdRaw => blobIdFactory.from(blobIdRaw))

  def getBlobIds(cassandraMessageId: CassandraMessageId): SFlux[BlobId] =
    getMapOfPositionBlobId(cassandraMessageId)
      .map(positionBlobIdMap => positionBlobIdMap
        .values
        .asScala)
      .flatMapMany(blobIds => SFlux.fromIterable(blobIds))
      .map(blobIdRow => blobIdFactory.from(blobIdRow))

  private def readRow(cassandraMessageId: CassandraMessageId, row: Row): EncryptedEmailDetailedView =
    EncryptedEmailDetailedView(
      id = cassandraMessageId,
      encryptedPreview = EncryptedPreview(row.getString(ENCRYPTED_PREVIEW)),
      encryptedHtml = EncryptedHtml(row.getString(ENCRYPTED_HTML)),
      hasAttachment = row.getBool(HAS_ATTACHMENT),
      encryptedAttachmentMetadata = Option(row.getString(ENCRYPTED_ATTACHMENT_METADATA))
        .map(value => EncryptedAttachmentMetadata(value)))

  private def getMapOfPositionBlobId(cassandraMessageId: CassandraMessageId): SMono[util.Map[Integer, String]] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind
      .setUUID(MESSAGE_ID, cassandraMessageId.get())))
      .map(row => row.get(POSITION_BLOB_ID_MAPPING, MAP_OF_POSITION_BLOBID_CODEC))
}
