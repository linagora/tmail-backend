package com.linagora.tmail.james.jmap.contact

import org.junit.jupiter.api.BeforeEach
import org.opensearch.index.query.QueryBuilder

class MemoryEmailAddressContactSearchEngineTest extends EmailAddressContactSearchEngineContract {
  var inMemoryEmailAddressContactSearchEngine: InMemoryEmailAddressContactSearchEngine = _

  override def testee(): EmailAddressContactSearchEngine = inMemoryEmailAddressContactSearchEngine

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryEmailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine()
  }

  override def awaitDocumentsIndexed(query: QueryType, documentCount: Long): Unit = {
  }
}
