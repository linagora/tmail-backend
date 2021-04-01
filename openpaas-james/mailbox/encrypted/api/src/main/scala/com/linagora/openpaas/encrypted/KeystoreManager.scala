package com.linagora.openpaas.encrypted

import java.util.UUID

import org.apache.james.core.Username
import org.reactivestreams.Publisher

object KeyId {
    def generate(): KeyId = KeyId(UUID.randomUUID())
}

case class KeyId(value: UUID)

case class PublicKey(id: KeyId, payload: Array[Byte])

trait KeystoreManager {

    def save(username: Username, payload: Array[Byte]): Publisher[KeyId]

    def listPublicKeys(username: Username): Publisher[PublicKey]

    def retrieveKey(username: Username, id: KeyId): Publisher[PublicKey]

    def delete(username: Username, id: KeyId): Publisher[Void]

    def deleteAll(username: Username): Publisher[Void]
}
