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

package com.linagora.tmail.james.jmap.settings

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.`type`.DataTypes.{TEXT, mapOf}
import com.datastax.oss.driver.api.core.`type`.codec.registry.CodecRegistry
import com.datastax.oss.driver.api.core.`type`.codec.{TypeCodec, TypeCodecs}
import com.datastax.oss.driver.api.core.cql.{BoundStatement, BoundStatementBuilder, PreparedStatement, Row}
import com.datastax.oss.driver.api.querybuilder.QueryBuilder.{bindMarker, insertInto, selectFrom, update}
import com.datastax.oss.driver.api.querybuilder.relation.Relation.column
import com.linagora.tmail.james.jmap.settings.CassandraJmapSettingsDAO.{ADD_SETTINGS, MAP_OF_STRING_CODEC, REMOVE_SETTINGS}
import com.linagora.tmail.user.cassandra.TMailCassandraUsersRepositoryDataDefinition.{SETTINGS, SETTINGS_STATE, TABLE_NAME, USER}
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor
import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

object CassandraJmapSettingsDAO {
  val ADD_SETTINGS: String = "add_settings"
  val REMOVE_SETTINGS: String = "remove_settings"
  val MAP_OF_STRING_CODEC: TypeCodec[java.util.Map[String,String]] = CodecRegistry.DEFAULT.codecFor(mapOf(TEXT, TEXT))
}

class CassandraJmapSettingsDAO @Inject()(session: CqlSession) {
  private val executor: CassandraAsyncExecutor = new CassandraAsyncExecutor(session)

  private val insertStatement: PreparedStatement = session.prepare(insertInto(TABLE_NAME)
    .value(USER, bindMarker(USER))
    .value(SETTINGS_STATE, bindMarker(SETTINGS_STATE))
    .value(SETTINGS, bindMarker(SETTINGS))
    .build())

  private val updateStatement: PreparedStatement = session.prepare(update(TABLE_NAME)
    .append(SETTINGS, bindMarker(ADD_SETTINGS))
    .remove(SETTINGS, bindMarker(REMOVE_SETTINGS))
    .setColumn(SETTINGS_STATE, bindMarker(SETTINGS_STATE))
    .where(column(USER).isEqualTo(bindMarker(USER)))
    .build())

  private val selectOne: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .columns(SETTINGS, SETTINGS_STATE)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  private val selectState: PreparedStatement = session.prepare(selectFrom(TABLE_NAME)
    .column(SETTINGS_STATE)
    .whereColumn(USER).isEqualTo(bindMarker(USER))
    .build())

  private val clearSettingsOfUser: PreparedStatement = session.prepare(update(TABLE_NAME)
    .setColumn(SETTINGS, bindMarker(SETTINGS))
    .setColumn(SETTINGS_STATE, bindMarker(SETTINGS_STATE))
    .where(column(USER).isEqualTo(bindMarker(USER)))
    .build())

  def selectOne(username: Username): SMono[JmapSettings] =
    SMono.fromPublisher(executor.executeSingleRow(selectOne.bind()
      .set(USER, username.asString(), TypeCodecs.TEXT)))
      .filter(userSettingsExists)
      .map(toJmapSettings)

  def selectState(username: Username): SMono[UuidState] =
    SMono.fromPublisher(executor.executeSingleRow(selectState.bind()
      .set(USER, username.asString(), TypeCodecs.TEXT))
      .filter(userSettingsExists)
      .map(row => UuidState(row.getUuid(SETTINGS_STATE))))

  def insertSetting(username: Username, newState: UuidState, newSettings: Map[JmapSettingsKey, JmapSettingsValue]): SMono[Void] = {
    val newSettingsJava: java.util.Map[String, String] = newSettings
      .map(entry => entry._1.asString() -> entry._2.value)
      .asJava

    val insertSetting: BoundStatement = insertStatement.bind()
      .set(USER, username.asString(), TypeCodecs.TEXT)
      .set(SETTINGS_STATE, newState.value, TypeCodecs.UUID)
      .set(SETTINGS, newSettingsJava, MAP_OF_STRING_CODEC)

    SMono(executor.executeVoid(insertSetting))
  }

  def updateSetting(username: Username, newState: UuidState, addSettings: Map[JmapSettingsKey, JmapSettingsValue], removeSettings: Seq[JmapSettingsKey]): SMono[Void] = {
    val updateStatementBuilder: BoundStatementBuilder = updateStatement.boundStatementBuilder()
    updateStatementBuilder.set(USER, username.asString(), TypeCodecs.TEXT)
    updateStatementBuilder.set(SETTINGS_STATE, newState.value, TypeCodecs.UUID)

    val addSettingsJava: java.util.Map[String, String] = addSettings
      .map(entry => entry._1.asString() -> entry._2.value)
      .asJava

    val removeSettingsJava: java.util.Set[String] = removeSettings
      .map(_.asString())
      .toSet
      .asJava

    if (addSettingsJava.isEmpty) {
      updateStatementBuilder.unset(ADD_SETTINGS)
    } else {
      updateStatementBuilder.setMap(ADD_SETTINGS, addSettingsJava, classOf[String], classOf[String])
    }

    if (removeSettingsJava.isEmpty) {
      updateStatementBuilder.unset(REMOVE_SETTINGS)
    } else {
      updateStatementBuilder.setSet(REMOVE_SETTINGS, removeSettingsJava, classOf[String])
    }

    SMono.fromPublisher(executor.executeVoid(updateStatementBuilder.build()))
  }

  def clearSettingsOfUser(username: Username): SMono[Void] =
    SMono.fromPublisher(executor.executeVoid(clearSettingsOfUser.bind()
      .set(USER, username.asString, TypeCodecs.TEXT)
      .setToNull(SETTINGS_STATE)
      .setToNull(SETTINGS)))

  private def toJmapSettings(row: Row): JmapSettings =
    JmapSettings(settings = toSettings(row), state = UuidState(row.get(SETTINGS_STATE, TypeCodecs.UUID)))

  private def toSettings(row: Row): Map[JmapSettingsKey, JmapSettingsValue] =
    row.getMap(SETTINGS, classOf[String], classOf[String])
      .entrySet()
      .asScala
      .map(entry => JmapSettingsKey.liftOrThrow(entry.getKey) -> JmapSettingsValue(entry.getValue))
      .toMap

  private def userSettingsExists(row: Row): Boolean =
    row.get(SETTINGS_STATE, TypeCodecs.UUID) != null
}