package com.linagora.tmail.james.app.modules.jmap

import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.james.jmap.label.{LabelChangeRepository, LabelRepository, LabelUserDeletionTaskStep, LabelUsernameChangeTaskStep, MemoryLabelChangeRepository, MemoryLabelRepository}
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}

case class MemoryLabelRepositoryModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[LabelRepository]).to(classOf[MemoryLabelRepository])
    bind(classOf[MemoryLabelRepository]).in(Scopes.SINGLETON)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[LabelUsernameChangeTaskStep])

    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[LabelUserDeletionTaskStep])

    bind(classOf[LabelChangeRepository]).to(classOf[MemoryLabelChangeRepository])
    bind(classOf[MemoryLabelChangeRepository]).in(Scopes.SINGLETON)
  }
}