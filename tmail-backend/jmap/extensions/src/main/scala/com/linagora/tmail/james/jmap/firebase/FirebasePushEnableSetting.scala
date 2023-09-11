package com.linagora.tmail.james.jmap.firebase

import java.util.Locale

import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.{JmapSettingEntry, JmapSettingParser}

case class FirebasePushEnableSetting(enabled: Boolean) extends JmapSettingEntry

case object FirebasePushEnableSettingParser extends JmapSettingParser[FirebasePushEnableSetting] {
  val ENABLED: FirebasePushEnableSetting = FirebasePushEnableSetting(defaultValue().value.toBoolean)

  override def key(): JmapSettingsKey = JmapSettingsKey.liftOrThrow("firebase.enabled")

  override def defaultValue(): JmapSettingsValue = JmapSettingsValue("true")

  override def parse(value: Option[JmapSettingsValue]): FirebasePushEnableSetting =
    value.map(_.value.toLowerCase(Locale.US)) match {
      case Some("false") => FirebasePushEnableSetting(false)
      case _ => ENABLED
    }
}
