package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.encrypted.{KeyId, PublicKey}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class KeystoreGetRequest(accountId: AccountId,
                              ids: List[KeyId]) extends WithAccountId

case class KeystoreGetResponse(accountId: AccountId,
                               state: UuidState,
                               list: Map[KeyId, PublicKey])
