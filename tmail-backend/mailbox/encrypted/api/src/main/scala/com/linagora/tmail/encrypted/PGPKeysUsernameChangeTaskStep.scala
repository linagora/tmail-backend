package com.linagora.tmail.encrypted

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class PGPKeysUsernameChangeTaskStep @Inject()(keystoreManager: KeystoreManager) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("PGPKeysUsernameChangeTaskStep")

  override def priority(): Int = 6

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] =
    SFlux(keystoreManager.listPublicKeys(oldUsername))
      .flatMap(publicKey => SMono(keystoreManager.save(newUsername, publicKey.key))
      .`then`(SMono(keystoreManager.delete(oldUsername, publicKey.id))))
}
