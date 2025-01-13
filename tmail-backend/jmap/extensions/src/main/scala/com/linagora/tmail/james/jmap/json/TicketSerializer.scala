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

package com.linagora.tmail.james.jmap.json

import java.net.InetAddress

import com.linagora.tmail.james.jmap.ticket.{Ticket, TicketValue}
import org.apache.james.core.Username
import play.api.libs.json.{JsString, JsValue, Json, Writes}

object TicketSerializer {
  implicit val ticketValueWrites: Writes[TicketValue] = ticketValue => JsString(ticketValue.value.toString)
  implicit val addressWrites: Writes[InetAddress] = address => JsString(address.getHostAddress)
  implicit val usernameWrites: Writes[Username] = username => JsString(username.asString())
  implicit val ticketWrites: Writes[Ticket] = Json.writes[Ticket]

  def serialize(ticket: Ticket): JsValue = Json.toJson(ticket)
}
