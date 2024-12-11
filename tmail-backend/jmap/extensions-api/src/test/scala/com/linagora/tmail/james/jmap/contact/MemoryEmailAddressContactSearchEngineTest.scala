package com.linagora.tmail.james.jmap.contact

import org.junit.jupiter.api.{BeforeEach, Disabled, Test}

class MemoryEmailAddressContactSearchEngineTest extends EmailAddressContactSearchEngineContract {
  var inMemoryEmailAddressContactSearchEngine: InMemoryEmailAddressContactSearchEngine = _

  override def testee(): EmailAddressContactSearchEngine = inMemoryEmailAddressContactSearchEngine

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryEmailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine()
  }

  override def awaitDocumentsIndexed(query: QueryType, documentCount: Long): Unit = {
  }

  @Test
  @Disabled("Memory does not need to support language special characters normalization")
  override def searchASCIICharactersShouldReturnMatchedFrenchName(): Unit = {
  }
}
