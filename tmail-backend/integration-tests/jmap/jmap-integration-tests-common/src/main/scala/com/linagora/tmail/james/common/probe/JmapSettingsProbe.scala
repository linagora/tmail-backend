package com.linagora.tmail.james.common.probe

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.settings.{JmapSettings, JmapSettingsKey, JmapSettingsRepository, JmapSettingsUpsertRequest, JmapSettingsValue, SettingsStateUpdate}
import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.SMono

import scala.jdk.CollectionConverters._

class JmapSettingsProbeModule extends AbstractModule {
  override def configure(): Unit =
    Multibinder.newSetBinder(binder(), classOf[GuiceProbe])
      .addBinding()
      .to(classOf[JmapSettingsProbe])
}

class JmapSettingsProbe @Inject()(jmapSettingsRepository: JmapSettingsRepository) extends GuiceProbe {
  def reset(username: Username, settings: JmapSettingsUpsertRequest): SettingsStateUpdate =
    SMono(jmapSettingsRepository.reset(username, settings)).block()

  def reset(username: Username, setting: Map[String, String]): SettingsStateUpdate = {
    val upsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(setting.map(kv => JmapSettingsKey.liftOrThrow(kv._1) -> JmapSettingsValue(kv._2)))
    reset(username, upsertRequest)
  }

  def reset(username: Username, settingsJava: java.util.Map[String, String]): SettingsStateUpdate = {
    val settingsScala: Map[String, String] = settingsJava.asScala.toMap
    val upsertRequest: JmapSettingsUpsertRequest = JmapSettingsUpsertRequest(settingsScala.map(kv => JmapSettingsKey.liftOrThrow(kv._1) -> JmapSettingsValue(kv._2)))
    reset(username, upsertRequest)
  }

  def get(username: Username): JmapSettings =
    SMono(jmapSettingsRepository.get(username)).block()

  def getLatestState(username: Username): UuidState =
    SMono(jmapSettingsRepository.getLatestState(username)).block()
}
