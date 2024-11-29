package com.linagora.tmail.james.jmap.label

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class LabelUserDeletionTaskStep @Inject()(labelRepository: LabelRepository) extends DeleteUserDataTaskStep {
  override def name(): DeleteUserDataTaskStep.StepName = new DeleteUserDataTaskStep.StepName("LabelUserDeletionTaskStep")

  override def priority(): Int = 9

  override def deleteUserData(username: Username): Publisher[Void] =
    SMono(labelRepository.deleteAllLabels(username))
}
