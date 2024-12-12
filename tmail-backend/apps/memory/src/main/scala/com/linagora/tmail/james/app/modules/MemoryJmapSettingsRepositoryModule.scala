package com.linagora.tmail.james.app.modules

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.settings.{JmapSettingsRepository, JmapSettingsUserDeletionTaskStep, JmapSettingsUsernameChangeTaskStep, MemoryJmapSettingsRepository}
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}

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