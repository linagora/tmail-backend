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
