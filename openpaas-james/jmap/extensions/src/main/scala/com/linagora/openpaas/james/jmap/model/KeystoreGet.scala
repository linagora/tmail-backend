package com.linagora.openpaas.james.jmap.model

import com.linagora.openpaas.encrypted.{KeyId, PublicKey}
import org.apache.james.jmap.core.{AccountId, State}
import org.apache.james.jmap.method.WithAccountId

case class KeystoreGetRequest(accountId: AccountId) extends WithAccountId

case class KeystoreGetResponse(accountId: AccountId, state: State, list: Map[KeyId, PublicKey])
