package com.linagora.tmail.james.jmap.ticket

import reactor.core.scala.publisher.SMono

import scala.collection.mutable

class MemoryTicketStore extends TicketStore {
  private val map: mutable.Map[TicketValue, Ticket] = mutable.Map()

  override def persist(ticket: Ticket): SMono[Unit] =
    SMono.fromCallable(() => map.put(ticket.value, ticket))
      .`then`

  override def retrieve(value: TicketValue): SMono[Ticket] = map.get(value)
    .map(SMono.just)
    .getOrElse(SMono.empty)

  override def delete(ticketValue: TicketValue): SMono[Unit] =
    SMono.fromCallable(() => map.remove(ticketValue))
      .`then`
}
