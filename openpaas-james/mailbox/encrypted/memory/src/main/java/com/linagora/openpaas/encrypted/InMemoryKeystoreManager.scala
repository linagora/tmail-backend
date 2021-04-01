package com.linagora.openpaas.encrypted
import org.apache.james.core.Username
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class InMemoryKeystoreManager(keystore: scala.collection.concurrent.Map[Username, Set[PublicKey]]) extends KeystoreManager {

  def this() {
    this(keystore = scala.collection.concurrent.TrieMap())
  }

  override def save(username: Username, payload: Array[Byte]): Publisher[KeyId] = {
    val keyId = KeyId.generate()
    validateUsername(username)
      .fold(_ => create(username, keyId, payload),
        _ => update(username, keyId, payload))
  }

  override def listPublicKeys(username: Username): Publisher[PublicKey] =
    validateUsername(username)
      .fold(_ => SFlux.empty,
        _ => SFlux.fromIterable(keystore(username)))

  override def retrieveKey(username: Username, keyId: KeyId): Publisher[PublicKey] = {
    val userKeys: SMono[Set[PublicKey]] = SMono.fromCallable(() => keystore(username))

    validateUsername(username)
      .fold(_ => SMono.empty,
        _ => userKeys.map(keys => keys.find(key => key.id.equals(keyId)))
          .filter(option => option.isDefined)
          .map(option => option.get)
          .switchIfEmpty(SMono.raiseError(new IllegalArgumentException(s"Cannot find key $keyId"))))
  }

  override def delete(username: Username, keyId: KeyId): Publisher[Void] =
    validateUsername(username)
      .map(_ => keystore(username))
      .flatMap(keys => checkKeyId(keys, keyId)
        .map(key => keystore.put(username, keys -- Set(key))))
      .fold(e => SMono.raiseError(e), _ => SMono.empty)

  private def checkKeyId(keys: Set[PublicKey], keyId: KeyId): Either[IllegalArgumentException, PublicKey] = {
    if (keys.exists(key => key.id.equals(keyId))) {
      Right(keys.find(k => k.id.equals(keyId)).get)
    } else {
      Left(new IllegalArgumentException(s"Could not find key $keyId"))
    }
  }

  override def deleteAll(username: Username): Publisher[Void] =
    validateUsername(username)
      .fold(e => SMono.raiseError(e),
        _ => SMono.fromCallable(() => keystore.remove(username)).`then`())

  private def create(username: Username, keyId: KeyId, payload: Array[Byte]): Publisher[KeyId] =
    SMono.fromCallable(() => keystore.put(username, Set(PublicKey(keyId, payload))))
      .map[KeyId](_ => keyId)

  private def update(username: Username, keyId: KeyId, payload: Array[Byte]): Publisher[KeyId] =
    validatePayload(username, payload)
      .fold(_ => updateWhenPayloadNotExist(username, keyId, payload),
        existingKeyId => SMono.just(existingKeyId))

  private def updateWhenPayloadNotExist(username: Username, keyId: KeyId, payload: Array[Byte]): SMono[KeyId] =
    SMono.fromCallable(() => keystore.put(username, keystore(username) ++ Set(PublicKey(keyId, payload))))
      .`then`(SMono.just(keyId))

  private def validateUsername(username: Username): Either[IllegalArgumentException, Boolean] =
    if (keystore.contains(username)) {
      Right(true)
    } else {
      Left(new IllegalArgumentException(s"Username ${username.asString} does not exist"))
    }

  private def validatePayload(username: Username, payload: Array[Byte]): Either[Unit, KeyId] = {
    keystore(username)
      .find(p => p.payload.sameElements(payload))
      .map(key => Right(key.id))
      .getOrElse(Left())
  }
}
