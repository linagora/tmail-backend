package com.linagora.tmail.james.jmap.label

import com.datastax.oss.driver.api.core.`type`.DataTypes.{TEXT, listOf}
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.`type`.codec.{TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.cql.{BoundStatementBuilder, PreparedStatement, Row}
import com.datastax.oss.driver.api.core.{CqlIdentifier, CqlSession}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, deleteFrom, insertInto, selectFrom, update}
import com.datastax.oss.driver.api.querybuilder.relation.Relation.column
import com.linagora.tmail.james.jmap.model.{Color, DisplayName, Label, LabelId}
import javax.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import org.apache.james.jmap.mail.Keyword
import reactor.core.scala.publisher.{SFlux, SMono}

object CassandraLabelTable {
  val TABLE_NAME = "labels"
  val USER: CqlIdentifier = CqlIdentifier.fromCql("user")
  val KEYWORD: CqlIdentifier = CqlIdentifier.fromCql("keyword")
  val DISPLAY_NAME: CqlIdentifier = CqlIdentifier.fromCql("display_name")
  val COLOR: CqlIdentifier = CqlIdentifier.fromCql("color")

  val MODULE: CassandraModule = CassandraModule.table(TABLE_NAME)
    .comment("Hold user JMAP labels")
    .statement(statement => _ => statement
      .withPartitionKey(USER, TEXT)
      .withClusteringColumn(KEYWORD, TEXT)
      .withColumn(DISPLAY_NAME, TEXT)
      .withColumn(COLOR, TEXT))
    .build

  val LIST_OF_STRINGS_CODEC: TypeCodec[java.util.List[String]] = CodecRegistry.DEFAULT.codecFor(listOf(TEXT))
}

class CassandraLabelDAO @Inject()(session: CqlSession) {
  import CassandraLabelTable._

  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insert: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USER, bindMarker(USER))
    .value(KEYWORD, bindMarker(KEYWORD))
    .value(DISPLAY_NAME, bindMarker(DISPLAY_NAME))
    .value(COLOR, bindMarker(COLOR))
    .build())

  private val updateStatement: PreparedStatement = session.prepare(update(TABLE_NAME)
    .setColumn(DISPLAY_NAME, bindMarker(DISPLAY_NAME))
    .setColumn(COLOR, bindMarker(COLOR))
    .where(column(USER).isEqualTo(bindMarker(USER)))
    .where(column(KEYWORD).isEqualTo(bindMarker(KEYWORD)))
    .build())

  private val selectAll: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  private val selectSome: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(KEYWORD).in(bindMarker(KEYWORD))
    .build())

  private val selectOne: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .all()
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
    .build())

  private val deleteOne: PreparedStatement = session.prepare(deleteFrom(TABLE_NAME)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .whereColumn(KEYWORD).isEqualTo(bindMarker(KEYWORD))
    .build())

  def insert(username: Username, label: Label): SMono[Label] = {
    val insertLabel = insert.bind()
      .set(USER, username.asString(), TypeCodecs.TEXT)
      .set(KEYWORD, label.keyword.flagName, TypeCodecs.TEXT)
      .set(DISPLAY_NAME, label.displayName.value, TypeCodecs.TEXT)
      .set(COLOR, label.color.map(_.value).orNull, TypeCodecs.TEXT)

    SMono.fromPublisher(executor.executeVoid(insertLabel)
      .thenReturn(label))
  }

  def selectAll(username: Username): SFlux[Label] =
    SFlux.fromPublisher(executor.executeRows(selectAll.bind()
      .set(USER, username.asString, TypeCodecs.TEXT))
      .map(toLabel))

  def selectSome(username: Username, keywords: java.util.Collection[Keyword]): SFlux[Label] = {
    val keywordStrings: java.util.List[String] = keywords.stream()
      .map(_.flagName)
      .toList
      .asInstanceOf[java.util.List[String]]

    val selectSomeStatement = selectSome.bind()
      .set(USER, username.asString, TypeCodecs.TEXT)
      .set(KEYWORD, keywordStrings, LIST_OF_STRINGS_CODEC)

    SFlux.fromPublisher(executor.executeRows(selectSomeStatement)
      .map(toLabel))
  }

  def selectOne(username: Username, keyword: Keyword): SMono[Label] =
    SMono.fromPublisher(executor.executeSingleRow(selectOne.bind()
      .set(USER, username.asString, TypeCodecs.TEXT)
      .set(KEYWORD, keyword.flagName, TypeCodecs.TEXT))
      .map(toLabel))

  def updateLabel(username: Username, keyword: Keyword, newDisplayName: Option[DisplayName], newColor: Option[Color]): SMono[Void] = {
    val updateStatementBuilder: BoundStatementBuilder = updateStatement.boundStatementBuilder()
    updateStatementBuilder.set(USER, username.asString, TypeCodecs.TEXT)
    updateStatementBuilder.set(KEYWORD, keyword.flagName, TypeCodecs.TEXT)

    newDisplayName match {
      case Some(displayName) => updateStatementBuilder.set(DISPLAY_NAME, displayName.value, TypeCodecs.TEXT)
      case None => updateStatementBuilder.unset(DISPLAY_NAME)
    }
    newColor match {
      case Some(color) => updateStatementBuilder.set(COLOR, color.value, TypeCodecs.TEXT)
      case None => updateStatementBuilder.unset(COLOR)
    }

    SMono.fromPublisher(executor.executeVoid(updateStatementBuilder.build()))
  }

  def deleteOne(username: Username, keyword: Keyword): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(deleteOne.bind()
      .set(USER, username.asString, TypeCodecs.TEXT)
      .set(KEYWORD, keyword.flagName, TypeCodecs.TEXT)))

  private def toLabel(row: Row) = {
    val keyword = Keyword.of(row.get(KEYWORD, TypeCodecs.TEXT)).get

    Label(id = LabelId.fromKeyword(keyword),
      displayName = DisplayName(row.get(DISPLAY_NAME, TypeCodecs.TEXT)),
      keyword = keyword,
      color = Option(row.get(COLOR, TypeCodecs.TEXT))
        .map(Color))
  }
}
