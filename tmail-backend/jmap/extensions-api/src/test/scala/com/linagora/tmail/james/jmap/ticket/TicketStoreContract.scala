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
import java.time.ZonedDateTime

import com.linagora.tmail.james.jmap.ticket.TicketStoreContract.initialDate
import org.apache.james.core.Username
import org.apache.james.jmap.core.UTCDate
import org.assertj.core.api.Assertions.{assertThat, assertThatCode}
import org.junit.jupiter.api.Test

object TicketStoreContract {
  val initialDate = ZonedDateTime.parse("2010-10-30T15:12:00Z[UTC]")
}

trait TicketStoreContract {
  def testee(): TicketStore

  @Test
  def retrieveShouldReturnNoneByDefault(): Unit = {
    assertThat(testee().retrieve(TicketValue.generate).blockOption())
      .isEqualTo(None)
  }

  @Test
  def retrieveShouldReturnPersistedValue(): Unit = {
    val ticket = Ticket(
      clientAddress = InetAddress.getByName("127.0.0.1"),
      validUntil = UTCDate(initialDate.plusMinutes(1)),
      generatedOn = UTCDate(initialDate),
      value = TicketValue.generate,
      username = Username.of("bob"))

    testee().persist(ticket).block()

    assertThat(testee().retrieve(ticket.value).blockOption())
      .isEqualTo(Some(ticket))
  }

  @Test
  def retrieveShouldNotReturnDeletedValue(): Unit = {
    val ticket = Ticket(
      clientAddress = InetAddress.getByName("127.0.0.1"),
      validUntil = UTCDate(initialDate.plusMinutes(1)),
      generatedOn = UTCDate(initialDate),
      value = TicketValue.generate,
      username = Username.of("bob"))

    testee().persist(ticket).block()

    testee().delete(ticket.value).block()

    assertThat(testee().retrieve(ticket.value).blockOption())
      .isEqualTo(None)
  }

  @Test
  def deleteShouldNotThrowWhenValueDoNotExist(): Unit = {
    assertThatCode(() => testee().delete(TicketValue.generate))
      .doesNotThrowAnyException()
  }
}
