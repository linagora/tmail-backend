package com.linagora.tmail.james.jmap.longlivedtoken

import org.junit.jupiter.api.BeforeEach

class InMemoryLongLivedTokenStoreTest extends LongLivedTokenStoreContract {

  var inMemoryLongLivedTokenStore: InMemoryLongLivedTokenStore = _

  override def testee: LongLivedTokenStore = inMemoryLongLivedTokenStore

  @BeforeEach
  def beforeEach(): Unit = {
    inMemoryLongLivedTokenStore = new InMemoryLongLivedTokenStore();
  }

}
