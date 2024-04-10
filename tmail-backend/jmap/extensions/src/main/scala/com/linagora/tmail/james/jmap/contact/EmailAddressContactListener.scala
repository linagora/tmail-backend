package com.linagora.tmail.james.jmap.contact

import jakarta.inject.Inject
import org.apache.james.events.EventListener.ReactiveGroupEventListener
import org.apache.james.events.{Event, Group}
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import org.slf4j.{Logger, LoggerFactory}
import reactor.core.scala.publisher.SMono

class EmailAddressContactListener @Inject()(contactSearchEngine: EmailAddressContactSearchEngine) extends ReactiveGroupEventListener {
  private val LOGGER: Logger = LoggerFactory.getLogger(classOf[EmailAddressContactListener])

  override def getDefaultGroup: Group = PushListenerGroup()

  override def reactiveEvent(event: Event): Publisher[Void] = {
    event match {
      case contactEvent: TmailContactUserAddedEvent => handlerContactUserAddedEvent(contactEvent)
      case _ => SMono.empty
    }
  }

  override def isHandling(event: Event): Boolean = event.isInstanceOf[TmailContactUserAddedEvent]

  private def handlerContactUserAddedEvent(event: TmailContactUserAddedEvent): Publisher[Void] =
    SMono.fromPublisher(contactSearchEngine.index(AccountId.fromUsername(event.username), event.contact))
      .doOnError(error => LOGGER.error("Error when indexing the new contact.", error))
      .`then`()
}

case class PushListenerGroup() extends Group {}