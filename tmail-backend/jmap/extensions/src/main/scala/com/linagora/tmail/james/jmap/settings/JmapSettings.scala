package com.linagora.tmail.james.jmap.settings

import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import org.apache.james.jmap.core.UuidState

object JmapSettings {

  private type SettingKeyRegex = MatchesRegex["^[a-zA-Z0-9-_#.]{1,255}$"]
  private type SettingKeyType = String Refined SettingKeyRegex

  object JmapSettingsKey {
    def liftOrThrow(value: String): JmapSettingsKey =
      refineV[SettingKeyRegex](value) match {
        case Left(error) => throw new IllegalArgumentException(error)
        case Right(settingKey) => JmapSettingsKey(settingKey)
      }
  }

  case class JmapSettingsKey(value: SettingKeyType) {
    def asString(): String = value.value
  }

  case class JmapSettingsValue(value: String) extends AnyVal
}

case class JmapSettings(settings: Map[JmapSettingsKey, JmapSettingsValue], state: UuidState)

case class JmapSettingsUpsertRequest(settings: Map[JmapSettingsKey, JmapSettingsValue])

case class JmapSettingsPatch(toUpsert: JmapSettingsUpsertRequest, toRemove: Seq[JmapSettingsKey]) {
  def isEmpty: Boolean = toUpsert.settings.isEmpty && toRemove.isEmpty

  def isConflict: Boolean = {
    val toUpsertKeys: Set[JmapSettingsKey] = toUpsert.settings.keySet
    val toRemoveKeys: Set[JmapSettingsKey] = toRemove.toSet
    toUpsertKeys.intersect(toRemoveKeys).nonEmpty
  }
}

object SettingsStateUpdate {
  def from(oldState: UuidState): SettingsStateUpdate =
    SettingsStateUpdate(oldState, JmapSettingsStateFactory.generateState())
}

case class SettingsStateUpdate(oldState: UuidState, newState: UuidState)

object JmapSettingsStateFactory {
  def generateState(): UuidState =
    UuidState.fromGenerateUuid()

  val INITIAL: UuidState = UuidState.INSTANCE
}