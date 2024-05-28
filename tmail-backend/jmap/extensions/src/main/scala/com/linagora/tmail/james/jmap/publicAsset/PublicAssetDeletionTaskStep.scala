package com.linagora.tmail.james.jmap.publicAsset

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

class PublicAssetDeletionTaskStep @Inject()(publicAssetRepository: PublicAssetRepository) extends DeleteUserDataTaskStep {
  override def name(): DeleteUserDataTaskStep.StepName = new DeleteUserDataTaskStep.StepName("PublicAssetDeletionTaskStep")

  override def priority(): Int = 9

  override def deleteUserData(username: Username): Publisher[Void] =
    SMono(publicAssetRepository.revoke(username))
}
