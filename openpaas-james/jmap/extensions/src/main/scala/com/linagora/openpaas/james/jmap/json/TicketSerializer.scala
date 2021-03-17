package com.linagora.openpaas.james.jmap.json

import java.net.{InetAddress, InetSocketAddress}
import java.time.format.DateTimeFormatter

import com.linagora.openpaas.james.jmap.ticket.{Ticket, TicketValue}
import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import play.api.libs.json.{JsString, JsValue, Json, Writes}

object TicketSerializer {
  implicit val ticketValueWrites: Writes[TicketValue] = ticketValue => JsString(ticketValue.value.toString)
  implicit val addressWrites: Writes[InetAddress] = address => JsString(address.toString)
  implicit val usernameWrites: Writes[Username] = username => JsString(username.asString())
  implicit val utcDateWrites: Writes[UTCDate] = date => JsString(date.asUTC.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")))
  implicit val ticketWrites: Writes[Ticket] = Json.writes[Ticket]

  def serialize(ticket: Ticket): JsValue = Json.toJson(ticket)
}
