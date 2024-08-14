package com.linagora.tmail.james.jmap.publicAsset

import java.time.Instant
import java.util.{Optional, UUID}

import com.datastax.oss.driver.api.core.`type`.DataTypes
import com.datastax.oss.driver.api.core.`type`.DataTypes.{BIGINT, TEXT, frozenSetOf, listOf}
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.`type`.codec.{TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.cql.{PreparedStatement, Row}
import com.datastax.oss.driver.api.core.{CqlIdentifier, CqlSession}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom}
import com.linagora.tmail.james.jmap.publicAsset.CassandraPublicAssetTable.{ASSET_ID, BLOB_ID, CONTENT_TYPE, CREATE_DATE, FROZEN_OF_UUIDS_CODEC, IDENTITY_IDS, LIST_OF_TIME_UUIDS_CODEC, PUBLIC_URI, SIZE, TABLE_NAME, USER}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.blob.api.BlobId
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{IdentityId, Size}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.javaapi.CollectionConverters

object CassandraPublicAssetTable {
  val TABLE_NAME = "public_asset"

  val USER: CqlIdentifier = CqlIdentifier.fromCql("user")
  val ASSET_ID: CqlIdentifier = CqlIdentifier.fromCql("asset_id")
  val PUBLIC_URI: CqlIdentifier = CqlIdentifier.fromCql("public_uri")
  val BLOB_ID: CqlIdentifier = CqlIdentifier.fromCql("blob_id")
  val SIZE: CqlIdentifier = CqlIdentifier.fromCql("size")
  val CONTENT_TYPE: CqlIdentifier = CqlIdentifier.fromCql("content_type")
  val IDENTITY_IDS: CqlIdentifier = CqlIdentifier.fromCql("identity_ids")
  val CREATE_DATE: CqlIdentifier = CqlIdentifier.fromCql("create_date")

  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Hold user public assets metadata")
    .statement(statement => _ => statement
      .withPartitionKey(USER, TEXT)
      .withClusteringColumn(ASSET_ID, DataTypes.TIMEUUID)
      .withColumn(PUBLIC_URI, TEXT)
      .withColumn(BLOB_ID, TEXT)
      .withColumn(SIZE, BIGINT)
      .withColumn(CONTENT_TYPE, TEXT)
      .withColumn(IDENTITY_IDS, frozenSetOf(DataTypes.UUID))
      .withColumn(CREATE_DATE, DataTypes.TIMESTAMP))
    .build

  val FROZEN_OF_UUIDS_CODEC: TypeCodec[java.util.Set[UUID]] = CodecRegistry.DEFAULT.codecFor(frozenSetOf(DataTypes.UUID))
  val LIST_OF_TIME_UUIDS_CODEC: TypeCodec[java.util.List[UUID]] = CodecRegistry.DEFAULT.codecFor(listOf(DataTypes.TIMEUUID))
}

class CassandraPublicAssetDAO @Inject()(session: CqlSession,
                                        val blobIdFactory: BlobId.Factory) {
  val CREATE_DATE_FALLBACK: Instant = Instant.EPOCH // 1970-01-01T00:00:00Z
  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insert: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USER, bindMarker(USER))
    .value(ASSET_ID, bindMarker(ASSET_ID))
    .value(PUBLIC_URI, bindMarker(PUBLIC_URI))
    .value(BLOB_ID, bindMarker(BLOB_ID))
    .value(SIZE, bindMarker(SIZE))
    .value(CONTENT_TYPE, bindMarker(CONTENT_TYPE))
    .value(IDENTITY_IDS, bindMarker(IDENTITY_IDS))
    .value(CREATE_DATE, bindMarker(CREATE_DATE))
    .build())

  private val selectAll: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  private val selectOne: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(ASSET_ID).isEqualTo(bindMarker(ASSET_ID))
    .build())

  private val selectSome: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(ASSET_ID).in(bindMarker(ASSET_ID))
    .build())

  private val selectAllBlobIdsStatement: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .column(BLOB_ID)
    .build())

  private val selectSize: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .column(SIZE)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  private val deleteOne: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(ASSET_ID).isEqualTo(bindMarker(ASSET_ID))
    .build())

  private val deleteAllAssets: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  def insertAsset(username: Username, publicAssetMetadata: PublicAssetMetadata): SMono[PublicAssetMetadata] =
    SMono(executor.executeVoid(insert.bind()
        .set(USER, username.asString, TypeCodecs.TEXT)
        .set(ASSET_ID, publicAssetMetadata.id.value, TypeCodecs.TIMEUUID)
        .set(PUBLIC_URI, publicAssetMetadata.publicURI.value.toString, TypeCodecs.TEXT)
        .set(BLOB_ID, publicAssetMetadata.blobId.asString(), TypeCodecs.TEXT)
        .set(SIZE, publicAssetMetadata.sizeAsLong(), TypeCodecs.BIGINT)
        .set(CONTENT_TYPE, publicAssetMetadata.contentType.value, TypeCodecs.TEXT)
        .set(IDENTITY_IDS, CollectionConverters.asJava(publicAssetMetadata.identityIds.map(_.id).toSet), FROZEN_OF_UUIDS_CODEC)
        .set(CREATE_DATE, publicAssetMetadata.createdDate, TypeCodecs.TIMESTAMP))
      .thenReturn(publicAssetMetadata))

  def selectAllAssets(username: Username): SFlux[PublicAssetMetadata] =
    SFlux(executor.executeRows(selectAll.bind()
        .set(USER, username.asString, TypeCodecs.TEXT))
      .map(toPublicAssetMetadata))

  def selectAsset(username: Username, assetId: PublicAssetId): SMono[PublicAssetMetadata] =
    SMono(executor.executeSingleRow(selectOne.bind()
        .set(USER, username.asString, TypeCodecs.TEXT)
        .set(ASSET_ID, assetId.value, TypeCodecs.TIMEUUID))
      .map(toPublicAssetMetadata))

  def selectAssets(username: Username, ids: Set[PublicAssetId]): SFlux[PublicAssetMetadata] = {
    val idsList: java.util.List[UUID] = CollectionConverters.asJava(ids)
      .stream()
      .map(_.value)
      .toList
      .asInstanceOf[java.util.List[UUID]]

    SFlux(executor.executeRows(selectSome.bind()
        .set(USER, username.asString, TypeCodecs.TEXT)
        .set(ASSET_ID, idsList, LIST_OF_TIME_UUIDS_CODEC)))
      .map(toPublicAssetMetadata)
  }

  def selectAllBlobIds(): SFlux[BlobId] =
    SFlux(executor.executeRows(selectAllBlobIdsStatement.bind())
      .map(row => blobIdFactory.parse(row.get(BLOB_ID, TypeCodecs.TEXT))))

  def selectSize(username: Username): SFlux[Long] =
    SFlux(executor.executeRows(selectSize.bind()
        .set(USER, username.asString, TypeCodecs.TEXT))
      .map(row => Size.sanitizeSize(row.get(SIZE, TypeCodecs.BIGINT)).value))

  def deleteAsset(username: Username, assetId: PublicAssetId): SMono[Unit] =
    SMono(executor.executeVoid(deleteOne.bind()
        .set(USER, username.asString, TypeCodecs.TEXT)
        .set(ASSET_ID, assetId.value, TypeCodecs.TIMEUUID)))
      .`then`()

  def deleteAllAssets(username: Username): SMono[Unit] =
    SMono(executor.executeVoid(deleteAllAssets.bind()
        .set(USER, username.asString, TypeCodecs.TEXT)))
      .`then`()

  private def toPublicAssetMetadata(row: Row) =
    PublicAssetMetadata(id = PublicAssetId(row.get(ASSET_ID, TypeCodecs.TIMEUUID)),
      publicURI = PublicURI.fromString(row.get(PUBLIC_URI, TypeCodecs.TEXT)).toOption.get,
      size = Size.sanitizeSize(row.get(SIZE, TypeCodecs.BIGINT)),
      contentType = ImageContentType.from(row.get(CONTENT_TYPE, TypeCodecs.TEXT)).toOption.get,
      blobId = blobIdFactory.parse(row.get(BLOB_ID, TypeCodecs.TEXT)),
      identityIds = CollectionConverters.asScala(row.get(IDENTITY_IDS, FROZEN_OF_UUIDS_CODEC))
        .map(IdentityId(_))
        .toSeq,
      createdDate = Optional.ofNullable(row.get(CREATE_DATE, TypeCodecs.TIMESTAMP)).orElse(CREATE_DATE_FALLBACK))
}
