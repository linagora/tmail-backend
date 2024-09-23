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
