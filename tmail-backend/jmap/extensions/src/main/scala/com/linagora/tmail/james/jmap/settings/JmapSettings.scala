package com.linagora.tmail.james.jmap.settings

import com.google.common.base.CharMatcher
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import eu.timepit.refined
import eu.timepit.refined.api.{Refined, Validate}
import org.apache.james.jmap.core.UuidState

object JmapSettings {
  case class JmapSettingKeyConstraint()

  type SettingKeyType = String Refined JmapSettingKeyConstraint

  private val charMatcher: CharMatcher = CharMatcher.inRange('a', 'z')
    .or(CharMatcher.inRange('0', '9'))
    .or(CharMatcher.inRange('A', 'Z'))
    .or(CharMatcher.is('.'))
    .or(CharMatcher.is('_'))
    .or(CharMatcher.is('-'))
    .or(CharMatcher.is('#'))
  def validateSettingKey(string: String): Either[IllegalArgumentException, SettingKeyType] =
    refined.refineV[JmapSettingKeyConstraint](string)(
        Validate.fromPredicate(s => s.nonEmpty && s.length < 256 && charMatcher.matchesAllOf(s),
          s => s"'$s' contains some invalid characters. Should be [#a-zA-Z0-9-_#.] and no longer than 255 chars",
          JmapSettingKeyConstraint()))
      .left.map(new IllegalArgumentException(_))

  object JmapSettingsKey {
    def liftOrThrow(value: String): JmapSettingsKey =
      validateSettingKey(value) match {
        case Left(error) => throw new IllegalArgumentException(error)
        case Right(settingKey) => JmapSettingsKey(settingKey)
      }

    def validate(value: String): Either[IllegalArgumentException, JmapSettingsKey] =
      validateSettingKey(value) match {
        case Left(error) => Left(error)
        case Right(settingKey) => Right(JmapSettingsKey(settingKey))
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