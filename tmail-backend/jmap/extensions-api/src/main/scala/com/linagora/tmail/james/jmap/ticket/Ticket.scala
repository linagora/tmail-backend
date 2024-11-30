package com.linagora.tmail.james.jmap.ticket

import java.net.InetAddress

import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate

case class Ticket(clientAddress: InetAddress,
                  value: TicketValue,
                  generatedOn: UTCDate,
                  validUntil: UTCDate,
                  username: Username)