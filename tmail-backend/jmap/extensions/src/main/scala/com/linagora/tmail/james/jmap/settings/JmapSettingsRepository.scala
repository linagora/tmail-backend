package com.linagora.tmail.james.jmap.settings

import com.google.common.base.Preconditions
import com.google.common.collect.{HashBasedTable, Table, Tables}
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.settings.JmapSettingsStateFactory.INITIAL
import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.collection.mutable

trait JmapSettingsRepository {
  def get(username: Username): Publisher[JmapSettings]

  def getLatestState(username: Username): Publisher[UuidState]

  def reset(username: Username, settings: JmapSettingsUpsertRequest): Publisher[SettingsStateUpdate]

  def updatePartial(username: Username, settingsPatch: JmapSettingsPatch): Publisher[SettingsStateUpdate]

  def delete(username: Username): Publisher[Void]
}

case class MemoryJmapSettingsRepository @Inject()() extends JmapSettingsRepository {

  import scala.jdk.CollectionConverters._

  private val settingsStore: Table[Username, JmapSettingsKey, JmapSettingsValue] =
    Tables.synchronizedTable(HashBasedTable.create())

  private val stateStore: mutable.Map[Username, UuidState] = mutable.Map()

  override def get(username: Username): SMono[JmapSettings] =
    SMono.justOrEmpty(stateStore.get(username))
      .map(state => {
        val settings: Map[JmapSettingsKey, JmapSettingsValue] = settingsStore.row(username).asScala.toMap
        JmapSettings(settings, state)
      })

  override def getLatestState(username: Username): Publisher[UuidState] =
    SMono.justOrEmpty(stateStore.get(username))
      .defaultIfEmpty(INITIAL)

  override def reset(username: Username, settings: JmapSettingsUpsertRequest): SMono[SettingsStateUpdate] =
    SMono.fromCallable(() => this.settingsStore.row(username).clear())
      .`then`(SMono.fromCallable(() => {
        settings.settings.foreach {
          case (key: JmapSettingsKey, value: JmapSettingsValue) => Option(this.settingsStore.put(username, key, value))
        }
        updateState(username)
      }).`then`(updateState(username)))

  override def updatePartial(username: Username, settingsPatch: JmapSettingsPatch): SMono[SettingsStateUpdate] = {
    Preconditions.checkArgument(!settingsPatch.isEmpty, "Cannot update when upsert and remove is empty".asInstanceOf[Object])
    Preconditions.checkArgument(!settingsPatch.isConflict, "Cannot update and remove the same setting key".asInstanceOf[Object])

    SMono.fromCallable(() => {
      settingsPatch.toUpsert.settings.foreach {
        case (key, value) => Option(this.settingsStore.put(username, key, value))
      }
      settingsPatch.toRemove.foreach(key => Option(this.settingsStore.remove(username, key)))
    }).`then`(updateState(username))
  }

  private def updateState(username: Username): SMono[SettingsStateUpdate] =
    SMono.fromCallable(() => {
      val newState: UuidState = JmapSettingsStateFactory.generateState()
      val oldState: UuidState = stateStore.getOrElse(username, INITIAL)
      this.stateStore.put(username, newState)
      SettingsStateUpdate(oldState, newState)
    })

  override def delete(username: Username): Publisher[Void] =
    SMono.fromCallable(() => {
      settingsStore.row(username).clear()
      stateStore.remove(username)
    })
      .`then`()
}

case class MemoryJmapSettingsRepositoryModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[MemoryJmapSettingsRepository]).in(Scopes.SINGLETON)
    bind(classOf[JmapSettingsRepository]).to(classOf[MemoryJmapSettingsRepository])
      .in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[JmapSettingsUsernameChangeTaskStep])

    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[JmapSettingsUserDeletionTaskStep])
  }
}