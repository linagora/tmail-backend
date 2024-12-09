package com.linagora.tmail.james.jmap.ticket

import java.util.UUID

import scala.util.Try

object TicketValue {
  def parse(string: String): Either[IllegalArgumentException, TicketValue] = Try(UUID.fromString(string))
    .map(TicketValue(_))
    .fold(e => Left(new IllegalArgumentException("TicketValue must be backed by a UUID", e)), Right(_))

  def generate: TicketValue = TicketValue(UUID.randomUUID())
}

case class TicketValue(value: UUID)