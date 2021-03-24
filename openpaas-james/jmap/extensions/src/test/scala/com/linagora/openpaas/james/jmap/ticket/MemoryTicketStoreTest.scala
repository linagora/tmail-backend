package com.linagora.openpaas.james.jmap.ticket

import org.junit.jupiter.api.BeforeEach

class MemoryTicketStoreTest extends TicketStoreContract {
  var testee: MemoryTicketStore = null;

  @BeforeEach
  def setUp(): Unit = {
    testee = new MemoryTicketStore()
  }
}
