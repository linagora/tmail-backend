/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.publicAsset

import java.util.UUID

import org.apache.james.backends.postgres.utils.PostgresExecutor
import org.apache.james.backends.postgres.{PostgresDataDefinition, PostgresIndex, PostgresTable}
import org.apache.james.blob.api.BlobId
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.{IdentityId, Size}
import org.jooq
import org.jooq.impl.DSL.sum
import org.jooq.impl.{DSL, SQLDataType}
import org.jooq.{Field, Record, Table}
import reactor.core.publisher.{Flux, Mono}
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.javaapi.CollectionConverters

object PublicAssetTable {
  val TABLE_NAME: Table[Record] = DSL.table("public_assets")

  val USER: Field[String] = DSL.field("username", SQLDataType.VARCHAR.notNull)
  val ASSET_ID: Field[UUID] = DSL.field("asset_id", SQLDataType.UUID.notNull)
  val BLOB_ID: Field[String] = DSL.field("blob_id", SQLDataType.VARCHAR.notNull)
  val SIZE: Field[java.lang.Long] = DSL.field("size", SQLDataType.BIGINT.notNull)
  val PUBLIC_URI: Field[String] = DSL.field("public_uri", SQLDataType.VARCHAR.notNull)
  val CONTENT_TYPE: Field[String] = DSL.field("content_type", SQLDataType.VARCHAR.notNull)
  val IDENTITIES_IDS: Field[Array[UUID]] = DSL.field("identity_ids", SQLDataType.UUID.getArrayDataType.notNull)

  val TABLE: PostgresTable = PostgresTable.name(TABLE_NAME.getName)
    .createTableStep((dsl, tableName) => dsl.createTableIfNotExists(tableName)
      .column(USER)
      .column(ASSET_ID)
      .column(BLOB_ID)
      .column(SIZE)
      .column(PUBLIC_URI)
      .column(CONTENT_TYPE)
      .column(IDENTITIES_IDS)
      .constraint(DSL.primaryKey(USER, ASSET_ID))
      .comment("Hold user public assets data"))
    .supportsRowLevelSecurity()
    .build()

  val USERNAME_INDEX: PostgresIndex = PostgresIndex.name("public_assets_username_index")
    .createIndexStep((dslContext, indexName) => dslContext.createIndexIfNotExists(indexName)
      .on(TABLE_NAME, USER))

  val MODULE: PostgresDataDefinition = PostgresDataDefinition.builder()
    .addTable(PublicAssetTable.TABLE)
    .addIndex(PublicAssetTable.USERNAME_INDEX)
    .build()
}

class PostgresPublicAssetDAO(postgresExecutor: PostgresExecutor, blobIdFactory: BlobId.Factory) {

  import PublicAssetTable._

  def insertAsset(username: Username, publicAssetMetadata: PublicAssetMetadata): SMono[PublicAssetMetadata] =
    SMono(postgresExecutor.executeVoid(dslContext => Mono.from(dslContext.insertInto(TABLE_NAME)
        .set(USER, username.asString())
        .set(ASSET_ID, publicAssetMetadata.id.value)
        .set(PUBLIC_URI, publicAssetMetadata.publicURI.value.toString)
        .set(BLOB_ID, publicAssetMetadata.blobId.asString())
        .set(SIZE, publicAssetMetadata.sizeAsLong())
        .set(CONTENT_TYPE, publicAssetMetadata.contentType.value)
        .set(IDENTITIES_IDS, publicAssetMetadata.identityIds.map(_.id).toArray)
        .onConflict(USER, ASSET_ID)
        .doUpdate()
        .set(PUBLIC_URI, publicAssetMetadata.publicURI.value.toString)
        .set(BLOB_ID, publicAssetMetadata.blobId.asString())
        .set(SIZE, publicAssetMetadata.sizeAsLong())
        .set(CONTENT_TYPE, publicAssetMetadata.contentType.value)
        .set(IDENTITIES_IDS, publicAssetMetadata.identityIds.map(_.id).toArray)))
      .thenReturn(publicAssetMetadata))

  def selectAllAssets(username: Username): SFlux[PublicAssetMetadata] =
    SFlux(postgresExecutor.executeRows(dslContext => Flux.from(dslContext.selectFrom(TABLE_NAME)
        .where(USER.eq(username.asString()))))
      .map(toMetadata(_)))

  def selectAllAssetsOrderByIdAsc(username: Username): SFlux[PublicAssetMetadata] =
    SFlux(postgresExecutor.executeRows(dslContext => Flux.from(dslContext.selectFrom(TABLE_NAME)
        .where(USER.eq(username.asString()))
        .orderBy(ASSET_ID.asc())))
      .map(toMetadata(_)))

  def selectAsset(username: Username, assetId: PublicAssetId): SMono[PublicAssetMetadata] =
    SMono(postgresExecutor.executeRow(dslContext => Mono.from(dslContext.selectFrom(TABLE_NAME)
        .where(USER.eq(username.asString()))
        .and(ASSET_ID.eq(assetId.value))))
      .map(toMetadata(_)))

  def selectAssets(username: Username, ids: Set[PublicAssetId]): SFlux[PublicAssetMetadata] =
    SFlux(postgresExecutor.executeRows(dslContext => Flux.from(dslContext.selectFrom(TABLE_NAME)
        .where(USER.eq(username.asString()))
        .and(ASSET_ID.in(CollectionConverters.asJava(ids.map(_.value))))))
      .map(toMetadata(_)))

  def selectAllBlobIds(): SFlux[BlobId] =
    SFlux(postgresExecutor.executeRows(dslContext => Flux.from(dslContext.select(BLOB_ID)
        .from(TABLE_NAME)))
      .map(record => blobIdFactory.parse(record.get(BLOB_ID))))

  def deleteAsset(username: Username, assetId: PublicAssetId): SMono[Void] =
    SMono(postgresExecutor.executeVoid(dslContext => Mono.from(dslContext.deleteFrom(TABLE_NAME)
      .where(USER.eq(username.asString()))
      .and(ASSET_ID.eq(assetId.value)))))

  def deleteAllAssets(username: Username): SMono[Void] =
    SMono(postgresExecutor.executeVoid(dslContext => Mono.from(dslContext.deleteFrom(TABLE_NAME)
      .where(USER.eq(username.asString())))))

  def selectSumSize(username: Username): SMono[Long] =
    SMono(postgresExecutor.executeRow(dslContext => Mono.from(dslContext.select(sum(SIZE))
        .from(TABLE_NAME)
        .where(USER.eq(username.asString()))))
      .map(record => record.get(0, classOf[Long])))

  private def toMetadata(record: jooq.Record) =
    PublicAssetMetadata(id = PublicAssetId(record.get(ASSET_ID)),
      publicURI = PublicURI.fromString(record.get(PUBLIC_URI)).toOption.get,
      size = Size.sanitizeSize(record.get(SIZE)),
      contentType = ImageContentType.from(record.get(CONTENT_TYPE)).toOption.get,
      blobId = blobIdFactory.parse(record.get(BLOB_ID)),
      identityIds = record.get(IDENTITIES_IDS)
        .map(IdentityId(_))
        .toSeq)
}
