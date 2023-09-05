package com.linagora.tmail.james.common.probe

import com.google.inject.AbstractModule
import com.google.inject.multibindings.Multibinder
import com.linagora.tmail.james.jmap.settings.JmapSettings.{JmapSettingsKey, JmapSettingsValue}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsRepository, JmapSettingsUpsertRequest, SettingsStateUpdate}
import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.UuidState
import org.apache.james.utils.GuiceProbe
import reactor.core.scala.publisher.SMono

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

  def getLatestState(username: Username): UuidState =
    SMono(jmapSettingsRepository.getLatestState(username)).block()
}
