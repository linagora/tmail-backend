package com.linagora.tmail.james.jmap.settings

import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.reactivestreams.Publisher

trait JmapSettingsRepository {
  def get(username: Username): Publisher[JmapSettings]

  def getLatestState(username: Username): Publisher[UuidState]

  def reset(username: Username, settings: JmapSettingsUpsertRequest): Publisher[SettingsStateUpdate]

  def updatePartial(username: Username, settingsPatch: JmapSettingsPatch): Publisher[SettingsStateUpdate]

  def delete(username: Username): Publisher[Void]
}
