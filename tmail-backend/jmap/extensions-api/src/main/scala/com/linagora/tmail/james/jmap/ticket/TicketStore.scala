package com.linagora.tmail.james.jmap.ticket

import reactor.core.scala.publisher.SMono

trait TicketStore {
  def persist(ticket: Ticket): SMono[Unit]

  def retrieve(value: TicketValue): SMono[Ticket]

  def delete(ticketValue: TicketValue): SMono[Unit]
}
