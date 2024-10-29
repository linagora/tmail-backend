package com.linagora.tmail.james.jmap.ticket

import java.net.InetAddress
import java.time.{Clock, ZonedDateTime}
import java.util.UUID

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration
import jakarta.inject.Inject
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
  private val validity: java.time.Duration = java.time.Duration.ofMinutes(1)
}

class TicketManager @Inject() (clock: Clock, ticketStore: TicketStore, jmapExtensionConfiguration: JMAPExtensionConfiguration) {

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
      .filter(validateIpIfNeeded(ip, _))
      .filter(ticket => ticket.validUntil.date.isAfter(ZonedDateTime.now(clock)))
      .map(_.username)
      .switchIfEmpty(SMono.error(ForbiddenException()))

  private def validateIpIfNeeded(ip: InetAddress, ticket: Ticket): Boolean =
    if (jmapExtensionConfiguration.ticketIpValidationEnable.value) {
      ticket.clientAddress.equals(ip)
    } else {
      true
    }

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