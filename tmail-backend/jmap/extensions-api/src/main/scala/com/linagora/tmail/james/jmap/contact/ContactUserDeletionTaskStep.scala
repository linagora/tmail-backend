package com.linagora.tmail.james.jmap.contact

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.api.model.AccountId
import org.apache.james.user.api.DeleteUserDataTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.OptionConverters._

class ContactUserDeletionTaskStep @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) extends DeleteUserDataTaskStep {
  override def name(): DeleteUserDataTaskStep.StepName = new DeleteUserDataTaskStep.StepName("ContactUserDataDeletionTaskStep")

  override def priority(): Int = 6

  override def deleteUserData(username: Username): Publisher[Void] = {
    val accountId: AccountId = AccountId.fromUsername(username)

    SFlux.fromPublisher(contactSearchEngine.list(accountId))
      .flatMap(contact => SMono.fromPublisher(contactSearchEngine.delete(accountId, contact.fields.address)))
      .`then`()
      .`then`(deleteDomainContact(username))
  }

  private def deleteDomainContact(username: Username): SMono[Void] =
    SMono.justOrEmpty(username.getDomainPart.toScala)
      .flatMap(domain => SMono(contactSearchEngine.delete(domain, username.asMailAddress())))
}
