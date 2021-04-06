package com.linagora.openpaas.encrypted
import java.io.ByteArrayInputStream

import com.google.common.io.BaseEncoding
import com.linagora.openpaas.gpg.Encrypter
import org.apache.james.core.Username
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

class InMemoryKeystoreManager (keystore: scala.collection.concurrent.Map[Username, Set[PublicKey]]) extends KeystoreManager {

  def this() {
    this(keystore = scala.collection.concurrent.TrieMap())
  }

  override def save(username: Username, payload: Array[Byte]): Publisher[KeyId] =
    validatePayload(payload)
      .fold(e => SMono.raiseError(e), keyId => validateUsername(username)
        .fold(_ => create(username, keyId, payload), _ => update(username, keyId, payload)))

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

  override def deleteAll(username: Username): Publisher[Void] =
    validateUsername(username)
      .fold(e => SMono.raiseError(e),
        _ => SMono.fromCallable(() => keystore.remove(username)).`then`())

  private def checkKeyId(keys: Set[PublicKey], keyId: KeyId): Either[IllegalArgumentException, PublicKey] = {
    if (keys.exists(key => key.id.equals(keyId))) {
      Right(keys.find(k => k.id.equals(keyId)).get)
    } else {
      Left(new IllegalArgumentException(s"Could not find key $keyId"))
    }
  }

  private def create(username: Username, keyId: KeyId, payload: Array[Byte]): Publisher[KeyId] =
    SMono.fromCallable(() => keystore.put(username, Set(PublicKey(keyId, payload))))
      .map[KeyId](_ => keyId)

  private def update(username: Username, keyId: KeyId, payload: Array[Byte]): Publisher[KeyId] =
    if (keystore(username).contains(PublicKey(keyId, payload))) {
      SMono.just(keyId)
    } else {
      SMono.fromCallable(() => keystore.put(username, keystore(username) ++ Set(PublicKey(keyId, payload))))
        .`then`(SMono.just(keyId))
    }

  private def validatePayload(payload: Array[Byte]): Either[Throwable, KeyId] = {
    Try(Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
      .toEither
      .map(key => KeyId(BaseEncoding.base16().encode(key.getFingerprint)))
  }

  private def validateUsername(username: Username): Either[IllegalArgumentException, Unit] =
    if (keystore.contains(username)) {
      Right()
    } else {
      Left(new IllegalArgumentException(s"Username ${username.asString} does not exist"))
    }
}
