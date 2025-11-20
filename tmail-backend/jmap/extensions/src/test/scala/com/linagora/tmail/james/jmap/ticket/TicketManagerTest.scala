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

import com.linagora.tmail.james.jmap.JMAPExtensionConfiguration.TICKET_IP_VALIDATION_ENABLED
import com.linagora.tmail.james.jmap.{JMAPExtensionConfiguration, TicketIpValidationEnable}
import org.apache.james.core.Username
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}

class TicketManagerTest {
  private val initialDate = ZonedDateTime.parse("2010-10-30T15:12:00Z")
  private val username = Username.of("bob")
  private val address = InetAddress.getByName("127.0.0.1")
  var clock: UpdatableTickingClock = null
  var jmapExtensionConfig: JMAPExtensionConfiguration = null
  var testee: TicketManager = null

  @BeforeEach
  def setUp(): Unit = {
    clock = new UpdatableTickingClock(initialDate.toInstant)
    jmapExtensionConfig = JMAPExtensionConfiguration()
    testee = new TicketManager(clock, new MemoryTicketStore(), jmapExtensionConfig.ticketIpValidationEnable.value)
  }

  @Test
  def ticketShouldBeScopedBySourceIpByDefault(): Unit = {
    val ticket = testee.generate(username, address).block()

    assertThatThrownBy(() => testee.validate(ticket.value, InetAddress.getByName("127.0.0.2")).block())
      .isInstanceOf(classOf[ForbiddenException])
  }

  @Test
  def ticketShouldBeScopedBySourceIpWhenIpValidationEnabledExplicitly(): Unit = {
    jmapExtensionConfig = JMAPExtensionConfiguration(ticketIpValidationEnable = TICKET_IP_VALIDATION_ENABLED)
    testee = new TicketManager(clock, new MemoryTicketStore(), jmapExtensionConfig.ticketIpValidationEnable.value)

    val ticket = testee.generate(username, address).block()

    assertThatThrownBy(() => testee.validate(ticket.value, InetAddress.getByName("127.0.0.2")).block())
      .isInstanceOf(classOf[ForbiddenException])
  }

  @Test
  def ticketShouldNotBeScopedBySourceIpWhenIpValidationDisabled(): Unit = {
    jmapExtensionConfig = JMAPExtensionConfiguration(ticketIpValidationEnable = TicketIpValidationEnable(false))
    testee = new TicketManager(clock, new MemoryTicketStore(), jmapExtensionConfig.ticketIpValidationEnable.value)

    val ticket = testee.generate(username, address).block()

    assertThat(testee.validate(ticket.value, InetAddress.getByName("127.0.0.2")).block())
      .isEqualTo(username)
  }

  @Test
  def ticketShouldExpire(): Unit = {
    val ticket = testee.generate(username, address).block()

    clock.setInstant(initialDate.plusMinutes(2).toInstant)

    assertThatThrownBy(() => testee.validate(ticket.value, address).block())
      .isInstanceOf(classOf[ForbiddenException])
  }

  @Test
  def ticketCanBeRevoked(): Unit = {
    val ticket = testee.generate(username, address).block()

    testee.revoke(ticket.value, username).block()

    assertThatThrownBy(() => testee.validate(ticket.value, address).block())
      .isInstanceOf(classOf[ForbiddenException])
  }

  @Test
  def ticketShouldBeSingleUse(): Unit = {
    val ticket = testee.generate(username, address).block()

    testee.validate(ticket.value, address).block()

    assertThatThrownBy(() => testee.validate(ticket.value, address).block())
      .isInstanceOf(classOf[ForbiddenException])
  }

  @Test
  def validateShouldReturnAssociatedUsername(): Unit = {
    val ticket = testee.generate(username, address).block()

    assertThat(testee.validate(ticket.value, address).block())
      .isEqualTo(username)
  }
}
