package com.linagora.openpaas.james.jmap.ticket

import java.net.InetSocketAddress
import java.util.UUID

import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import reactor.core.scala.publisher.SMono

import scala.util.Try

object TicketValue {
  def parse(string: String): Either[IllegalArgumentException, TicketValue] = Try(UUID.fromString(string))
    .map(TicketValue(_))
    .fold(e => Left(new IllegalArgumentException("TicketValue must be backed by a UUID", e)), Right(_))
}

case class ForbiddenException() extends RuntimeException
case class TicketValue(value: UUID)
case class Ticket(remoteAddress: InetSocketAddress,
                  value: TicketValue,
                  generatedOn: UTCDate,
                  validUntil: UTCDate,
                  username: Username)

trait TicketManager {
  def generate(username: Username): SMono[Ticket]

  def validate(ticketValue: TicketValue, ip: InetSocketAddress): SMono[Username]

  def revoke(ticketValue: TicketValue, username: Username): SMono[Unit]
}
