package com.linagora.tmail.encrypted

import com.google.common.io.BaseEncoding
import org.apache.james.core.Username
import org.reactivestreams.Publisher

object KeyId {
  def fromPayload(payload: Array[Byte]): KeyId = KeyId(BaseEncoding.base16().encode(payload))
}

case class KeyId(value: String)

case class PublicKey(id: KeyId, payload: Array[Byte]) {
  def hasSameContentAs(publicKey: PublicKey): Boolean =
    id.equals(publicKey.id) && payload.sameElements(publicKey.payload)
}

trait KeystoreManager {

  def save(username: Username, payload: Array[Byte]): Publisher[KeyId]

  def listPublicKeys(username: Username): Publisher[PublicKey]

  def retrieveKey(username: Username, id: KeyId): Publisher[PublicKey]

  def delete(username: Username, id: KeyId): Publisher[Void]

  def deleteAll(username: Username): Publisher[Void]
}
