/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.james.jmap.ticket

import java.net.InetAddress
import java.time.{Clock, ZonedDateTime}

import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import reactor.core.scala.publisher.SMono

object TicketManager {
  private val validity: java.time.Duration = java.time.Duration.ofMinutes(1)
}

class TicketManager (clock: Clock, ticketStore: TicketStore, ticketIpValidationEnabled: Boolean) {

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
    if (ticketIpValidationEnabled) {
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