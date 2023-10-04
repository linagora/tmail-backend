package com.linagora.tmail.james.jmap.settings

import org.apache.james.core.Username
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

case class JmapSettingsRepositoryJavaUtils(jmapSettingsRepository: JmapSettingsRepository) {
  def reset(username: Username, settingsJava: java.util.Map[String, String]): SettingsStateUpdate = {
    val settingsScala: Map[String, String] = settingsJava.asScala.toMap
    val upsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(settingsScala.map(kv => JmapSettingsKey.liftOrThrow(kv._1) -> JmapSettingsValue(kv._2)))
    SMono(jmapSettingsRepository.reset(username, upsertRequest)).block()
  }
}
