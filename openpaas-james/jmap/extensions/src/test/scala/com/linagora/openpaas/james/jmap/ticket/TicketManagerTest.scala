package com.linagora.openpaas.james.jmap.ticket

import java.net.InetAddress
import java.time.ZonedDateTime

import org.apache.james.core.Username
import org.apache.james.utils.UpdatableTickingClock
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.junit.jupiter.api.{BeforeEach, Test}

class TicketManagerTest {
  private val initialDate = ZonedDateTime.parse("2010-10-30T15:12:00Z")
  private val username = Username.of("bob")
  private val address = InetAddress.getByName("127.0.0.1")
  var clock: UpdatableTickingClock = null
  var testee: TicketManager = null

  @BeforeEach
  def setUp(): Unit = {
    clock = new UpdatableTickingClock(initialDate.toInstant)
    testee = new TicketManager(clock, new MemoryTicketStore())
  }

  @Test
  def ticketShouldBeScopedBySourceIp(): Unit = {
    val ticket = testee.generate(username, address).block()

    assertThatThrownBy(() => testee.validate(ticket.value, InetAddress.getByName("127.0.0.2")).block())
      .isInstanceOf(classOf[ForbiddenException])
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
