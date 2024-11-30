package com.linagora.tmail.james.jmap.settings

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class JmapSettingsUsernameChangeTaskStep @Inject()(repository: JmapSettingsRepository) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("JmapSettingsUsernameChangeTaskStep")

  override def priority(): Int = 9

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] =
    SMono(repository.get(oldUsername))
      .flatMap(oldAccountSettings => SMono(repository.get(newUsername))
        .flatMap(newAccountSettings => mergeSettingsAndFullReset(newUsername, oldAccountSettings, newAccountSettings))
        .switchIfEmpty(overrideWithOldAccountSettings(newUsername, oldAccountSettings))
        .`then`(SMono(repository.delete(oldUsername))))

  private def mergeSettingsAndFullReset(newUsername: Username, oldAccountSettings: JmapSettings, newAccountSettings: JmapSettings): SMono[SettingsStateUpdate] =
    SMono(repository.reset(newUsername, JmapSettingsUpsertRequest(oldAccountSettings.settings ++ newAccountSettings.settings)))

  private def overrideWithOldAccountSettings(newUsername: Username, oldAccountSettings: JmapSettings): SMono[SettingsStateUpdate] =
    SMono(repository.reset(newUsername, JmapSettingsUpsertRequest(oldAccountSettings.settings)))
}
