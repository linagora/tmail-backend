package com.linagora.tmail.james.jmap.settings

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class JmapSettingsUserDeletionTaskStep @Inject()(repository: JmapSettingsRepository) extends DeleteUserDataTaskStep {
  override def name(): DeleteUserDataTaskStep.StepName = new DeleteUserDataTaskStep.StepName("JmapSettingsUserDeletionTaskStep")

  override def priority(): Int = 10

  override def deleteUserData(username: Username): Publisher[Void] =
    SMono(repository.delete(username))
}
