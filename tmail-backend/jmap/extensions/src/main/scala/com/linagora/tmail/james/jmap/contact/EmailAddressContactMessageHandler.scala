package com.linagora.tmail.james.jmap.contact

import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import javax.inject.Inject

class EmailAddressContactMessageHandler @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) {

  def handler(message: EmailAddressContactMessage): Publisher[Unit] =
    message.scope match {
      case User => contactUserMessageHandler(message)
      case Domain => contactDomainMessageHandler(message)
    }

  private def contactUserMessageHandler(message: EmailAddressContactMessage): SMono[Unit] =
    ContactOwner.asUsername(message.owner)
      .map(AccountId.fromUsername)
      .map(accountId => message.messageType match {
        case Addition => SMono.fromPublisher(contactSearchEngine.index(accountId, MessageEntry.toContactField(message.entry))).`then`()
        case Update => SMono.fromPublisher(contactSearchEngine.update(accountId, MessageEntry.toContactField(message.entry))).`then`()
        case Removal => SMono.fromPublisher(contactSearchEngine.delete(accountId, message.entry.address)).`then`()
      })
      .fold(SMono.error, success => success)

  private def contactDomainMessageHandler(message: EmailAddressContactMessage): SMono[Unit] =
    ContactOwner.asDomain(message.owner)
      .map(domain => message.messageType match {
        case Addition => SMono.fromPublisher(contactSearchEngine.index(domain, MessageEntry.toContactField(message.entry))).`then`()
        case Update => SMono.fromPublisher(contactSearchEngine.update(domain, MessageEntry.toContactField(message.entry))).`then`()
        case Removal => SMono.fromPublisher(contactSearchEngine.delete(domain, message.entry.address)).`then`()
      })
      .fold(SMono.error, success => success)

}
