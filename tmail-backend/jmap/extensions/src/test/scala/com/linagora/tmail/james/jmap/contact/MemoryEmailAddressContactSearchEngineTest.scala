package com.linagora.tmail.james.jmap.contact

import org.elasticsearch.index.query.QueryBuilder
import org.junit.jupiter.api.BeforeEach

class MemoryEmailAddressContactSearchEngineTest extends EmailAddressContactSearchEngineContract {
  var inMemoryEmailAddressContactSearchEngine: InMemoryEmailAddressContactSearchEngine = _

  override def testee(): EmailAddressContactSearchEngine = inMemoryEmailAddressContactSearchEngine

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryEmailAddressContactSearchEngine = new InMemoryEmailAddressContactSearchEngine()
  }

  override def awaitDocumentsIndexed(query: QueryBuilder, documentCount: Long): Unit = {
  }
}
