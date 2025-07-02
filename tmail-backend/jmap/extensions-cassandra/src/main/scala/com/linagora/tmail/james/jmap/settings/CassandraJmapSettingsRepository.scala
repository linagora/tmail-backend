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

import com.google.common.base.Preconditions
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.settings.JmapSettingsStateFactory.INITIAL
import jakarta.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraDataDefinition
import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class CassandraJmapSettingsRepository @Inject()(dao: CassandraJmapSettingsDAO) extends JmapSettingsRepository {
  override def get(username: Username): Publisher[JmapSettings] = dao.selectOne(username)

  override def reset(username: Username, settings: JmapSettingsUpsertRequest): Publisher[SettingsStateUpdate] =
    dao.selectState(username)
      .defaultIfEmpty(INITIAL)
      .flatMap(oldState => {
        val newState: UuidState = JmapSettingsStateFactory.generateState()
        dao.insertSetting(username, newState, settings.settings)
          .`then`(SMono.just(SettingsStateUpdate(oldState, newState)))
      })

  override def updatePartial(username: Username, settingsPatch: JmapSettingsPatch): Publisher[SettingsStateUpdate] = {
    Preconditions.checkArgument(!settingsPatch.isEmpty, "Cannot update when upsert and remove is empty".asInstanceOf[Object])
    Preconditions.checkArgument(!settingsPatch.isConflict, "Cannot update and remove the same setting key".asInstanceOf[Object])

    val newState: UuidState = JmapSettingsStateFactory.generateState()
    dao.selectState(username)
      .defaultIfEmpty(INITIAL)
      .flatMap(oldState => dao.updateSetting(username, newState, settingsPatch.toUpsert.settings, settingsPatch.toRemove)
          .`then`(SMono.just(SettingsStateUpdate(oldState, newState))))
  }

  override def getLatestState(username: Username): Publisher[UuidState] =
    dao.selectState(username)
      .defaultIfEmpty(INITIAL)

  override def delete(username: Username): Publisher[Void] = dao.deleteOne(username)
}

case class CassandraJmapSettingsRepositoryModule() extends AbstractModule {
  override def configure(): Unit = {
    Multibinder.newSetBinder(binder, classOf[CassandraDataDefinition])
      .addBinding().toInstance(CassandraJmapSettingsTable.MODULE)
    bind(classOf[CassandraJmapSettingsDAO]).in(Scopes.SINGLETON)

    bind(classOf[JmapSettingsRepository]).to(classOf[CassandraJmapSettingsRepository])
    bind(classOf[CassandraJmapSettingsRepository]).in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[JmapSettingsUsernameChangeTaskStep])

    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[JmapSettingsUserDeletionTaskStep])
  }
}