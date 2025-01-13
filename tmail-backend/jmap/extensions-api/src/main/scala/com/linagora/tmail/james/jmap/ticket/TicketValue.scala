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

import java.util.UUID

import scala.util.Try

object TicketValue {
  def parse(string: String): Either[IllegalArgumentException, TicketValue] = Try(UUID.fromString(string))
    .map(TicketValue(_))
    .fold(e => Left(new IllegalArgumentException("TicketValue must be backed by a UUID", e)), Right(_))

  def generate: TicketValue = TicketValue(UUID.randomUUID())
}

case class TicketValue(value: UUID)