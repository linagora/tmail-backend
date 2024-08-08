package com.linagora.tmail.encrypted.cassandra

import java.util

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodec
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, Row}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.linagora.tmail.encrypted.cassandra.table.EncryptedEmailTable.{ENCRYPTED_ATTACHMENT_METADATA, ENCRYPTED_HTML, ENCRYPTED_PREVIEW, HAS_ATTACHMENT, MESSAGE_ID, POSITION_BLOB_ID_MAPPING, TABLE_NAME}
import com.linagora.tmail.encrypted.{EncryptedAttachmentMetadata, EncryptedEmailDetailedView, EncryptedHtml, EncryptedPreview}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.blob.api.{BlobId, BlobReferenceSource}
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

class CassandraEncryptedEmailDAO @Inject()(session: CqlSession, blobIdFactory: BlobId.Factory) {
  private val MAP_OF_POSITION_BLOBID_CODEC: TypeCodec[util.Map[java.lang.Integer, String]] =
    CodecRegistry.DEFAULT.codecFor(DataTypes.frozenMapOf(DataTypes.INT, DataTypes.TEXT))

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(MESSAGE_ID, bindMarker(MESSAGE_ID))
    .value(ENCRYPTED_PREVIEW, bindMarker(ENCRYPTED_PREVIEW))
    .value(ENCRYPTED_HTML, bindMarker(ENCRYPTED_HTML))
    .value(HAS_ATTACHMENT, bindMarker(HAS_ATTACHMENT))
    .value(ENCRYPTED_ATTACHMENT_METADATA, bindMarker(ENCRYPTED_ATTACHMENT_METADATA))
    .value(POSITION_BLOB_ID_MAPPING, bindMarker(POSITION_BLOB_ID_MAPPING))
    .build())

  private val listBlobIdsStatement: PreparedStatement = session.prepare(
    selectFrom(TABLE_NAME).column(POSITION_BLOB_ID_MAPPING).build())

  private val selectStatement: PreparedStatement = session.prepare(
    selectFrom(TABLE_NAME)
      .all()
      .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
      .build())

  private val deleteStatement: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(MESSAGE_ID).isEqualTo(bindMarker(MESSAGE_ID))
    .build())

  def insert(cassandraMessageId: CassandraMessageId, encryptedEmailDetailed: EncryptedEmailDetailedView, positionBlobIdMapping: Map[Int, BlobId]): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(insertStatement.bind()
      .setUuid(MESSAGE_ID, cassandraMessageId.get())
      .setString(ENCRYPTED_PREVIEW, encryptedEmailDetailed.encryptedPreview.value)
      .setString(ENCRYPTED_HTML, encryptedEmailDetailed.encryptedHtml.value)
      .setBoolean(HAS_ATTACHMENT, encryptedEmailDetailed.hasAttachment)
      .setString(ENCRYPTED_ATTACHMENT_METADATA, encryptedEmailDetailed.encryptedAttachmentMetadata
        .map(metadata => metadata.value)
        .orNull)
      .setMap(POSITION_BLOB_ID_MAPPING, positionBlobIdMapping
        .map(mapping => Integer.valueOf(mapping._1) -> mapping._2.asString())
        .asJava, classOf[Integer], classOf[String])))
      .`then`()

  def get(cassandraMessageId: CassandraMessageId): SMono[EncryptedEmailDetailedView] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind()
      .setUuid(MESSAGE_ID, cassandraMessageId.get())))
      .map(row => readRow(cassandraMessageId, row))

  def delete(cassandraMessageId: CassandraMessageId): SMono[Unit] =
    SMono.fromPublisher(executor.executeVoid(deleteStatement.bind()
      .setUuid(MESSAGE_ID, cassandraMessageId.get())))
      .`then`()

  def getBlobId(cassandraMessageId: CassandraMessageId, position: Int): SMono[BlobId] =
    getMapOfPositionBlobId(cassandraMessageId)
      .map(positionBlobIdMapping => positionBlobIdMapping.get(position))
      .map(blobIdFactory.parse)

  def getBlobIds(cassandraMessageId: CassandraMessageId): SFlux[BlobId] =
    getMapOfPositionBlobId(cassandraMessageId)
      .map(positionBlobIdMap => positionBlobIdMap
        .values
        .asScala)
      .flatMapMany(blobIds => SFlux.fromIterable(blobIds))
      .map(blobIdFactory.parse)

  def listBlobIds(): SFlux[BlobId] = SFlux(executor.executeRows(listBlobIdsStatement.bind()))
    .flatMapIterable(row => row.getMap(POSITION_BLOB_ID_MAPPING, classOf[Integer], classOf[String]).asScala.toMap.values)
    .map(blobIdFactory.parse)

  private def readRow(cassandraMessageId: CassandraMessageId, row: Row): EncryptedEmailDetailedView =
    EncryptedEmailDetailedView(
      id = cassandraMessageId,
      encryptedPreview = EncryptedPreview(row.getString(ENCRYPTED_PREVIEW)),
      encryptedHtml = EncryptedHtml(row.getString(ENCRYPTED_HTML)),
      hasAttachment = row.getBoolean(HAS_ATTACHMENT),
      encryptedAttachmentMetadata = Option(row.getString(ENCRYPTED_ATTACHMENT_METADATA))
        .map(value => EncryptedAttachmentMetadata(value)))

  private def getMapOfPositionBlobId(cassandraMessageId: CassandraMessageId): SMono[util.Map[Integer, String]] =
    SMono.fromPublisher(executor.executeSingleRow(selectStatement.bind()
      .setUuid(MESSAGE_ID, cassandraMessageId.get())))
      .map(row => row.get(POSITION_BLOB_ID_MAPPING, MAP_OF_POSITION_BLOBID_CODEC))
}

class EncryptedEmailBlobReferenceSource @Inject()(dao: CassandraEncryptedEmailDAO) extends BlobReferenceSource {
  override def listReferencedBlobs(): Publisher[BlobId] = dao.listBlobIds()
}
