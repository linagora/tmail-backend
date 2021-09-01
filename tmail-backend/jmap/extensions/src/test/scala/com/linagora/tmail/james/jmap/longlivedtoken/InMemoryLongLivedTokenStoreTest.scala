package com.linagora.tmail.james.jmap.longlivedtoken

class InMemoryLongLivedTokenStoreTest extends LongLivedTokenStoreContract {
  override def getLongLivedTokenStore: LongLivedTokenStore = new InMemoryLongLivedTokenStore()
}
