package com.linagora.openpaas.james.jmap.ticket

import java.net.InetAddress
import java.time.{Clock, ZonedDateTime}
import java.util.UUID

import javax.inject.Inject
import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import reactor.core.scala.publisher.SMono

import scala.collection.mutable
import scala.util.Try

object TicketValue {
  def parse(string: String): Either[IllegalArgumentException, TicketValue] = Try(UUID.fromString(string))
    .map(TicketValue(_))
    .fold(e => Left(new IllegalArgumentException("TicketValue must be backed by a UUID", e)), Right(_))

  def generate: TicketValue = TicketValue(UUID.randomUUID())
}

case class ForbiddenException() extends RuntimeException
case class TicketValue(value: UUID)
case class Ticket(clientAddress: InetAddress,
                  value: TicketValue,
                  generatedOn: UTCDate,
                  validUntil: UTCDate,
                  username: Username)

object TicketManager {
  val validity: java.time.Duration = java.time.Duration.ofMinutes(1)
}

class TicketManager @Inject() (clock: Clock, ticketStore: TicketStore) {
  def generate(username: Username, remoteAddress: InetAddress): SMono[Ticket] = {
    val now = ZonedDateTime.now(clock)
    val validityEnd = now.plus(TicketManager.validity)

    val ticket = Ticket(clientAddress = remoteAddress,
      value = TicketValue.generate,
      username = username,
      generatedOn = UTCDate(now),
      validUntil = UTCDate(validityEnd))

    ticketStore.persist(ticket)
      .`then`(SMono.just(ticket))
  }

  def validate(ticketValue: TicketValue, ip: InetAddress): SMono[Username] =
    ticketStore.retrieve(ticketValue)
      .flatMap(ticket => ticketStore.delete(ticket.value)
        .`then`(SMono.just(ticket)))
      .filter(ticket => ticket.clientAddress.equals(ip))
      .filter(ticket => ticket.validUntil.date.isAfter(ZonedDateTime.now(clock)))
      .map(_.username)
      .switchIfEmpty(SMono.error(ForbiddenException()))

  def revoke(ticketValue: TicketValue, username: Username): SMono[Unit] =
    ticketStore.retrieve(ticketValue)
      .filter(ticket => ticket.username.equals(username))
      .map(_.value)
      .switchIfEmpty(SMono.error(ForbiddenException()))
      .flatMap(ticketStore.delete)
}

trait TicketStore {
  def persist(ticket: Ticket): SMono[Unit]

  def retrieve(value: TicketValue): SMono[Ticket]

  def delete(ticketValue: TicketValue): SMono[Unit]
}
