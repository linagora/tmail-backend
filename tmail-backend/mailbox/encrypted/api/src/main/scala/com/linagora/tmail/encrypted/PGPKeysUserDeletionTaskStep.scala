package com.linagora.tmail.encrypted

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class PGPKeysUserDeletionTaskStep @Inject()(keystoreManager: KeystoreManager) extends DeleteUserDataTaskStep {
  override def name(): DeleteUserDataTaskStep.StepName = new DeleteUserDataTaskStep.StepName("PGPKeysUserDeletionTaskStep")

  override def priority(): Int = 7

  override def deleteUserData(username: Username): Publisher[Void] =
    SMono.fromPublisher(keystoreManager.deleteAll(username))
}
