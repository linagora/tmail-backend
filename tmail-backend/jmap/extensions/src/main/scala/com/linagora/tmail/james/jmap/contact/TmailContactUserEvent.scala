package com.linagora.tmail.james.jmap.contact

import org.apache.james.core.{Domain, MailAddress, Username}
import org.apache.james.events.Event
import org.apache.james.events.Event.EventId

trait TmailEvent extends Event

sealed abstract class TmailContactUserEvent(eventId: EventId, username: Username) extends TmailEvent {
  override def isNoop: Boolean = false

  override def getEventId: EventId = eventId

  override def getUsername: Username = username
}

case class TmailContactUserAddedEvent(eventId: EventId, username: Username, contact: ContactFields) extends TmailContactUserEvent(eventId, username)

case class TmailContactUserRemovedEvent(eventId: EventId, username: Username, address: MailAddress) extends TmailContactUserEvent(eventId, username)

case class TmailContactUserUpdatedEvent(eventId: EventId, username: Username, contact: ContactFields) extends TmailContactUserEvent(eventId, username)

sealed abstract class TmailContactDomainEvent(eventId: EventId, domain: Domain) extends TmailEvent {
  override def isNoop: Boolean = false

  override def getEventId: EventId = eventId

  override def getUsername: Username = ???
}

case class TmailContactDomainAddedEvent(eventId: EventId, domain: Domain, contact: ContactFields) extends TmailContactDomainEvent(eventId, domain)

case class TmailContactDomainUpdatedEvent(eventId: EventId, domain: Domain, contact: ContactFields) extends TmailContactDomainEvent(eventId, domain)

case class TmailContactDomainRemovedEvent(eventId: EventId, domain: Domain, address: MailAddress) extends TmailContactDomainEvent(eventId, domain)

