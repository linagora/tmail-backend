package com.linagora.tmail.encrypted

import java.io.ByteArrayInputStream

import com.google.common.io.BaseEncoding
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.pgp.Encrypter
import org.apache.james.core.Username
import org.apache.james.user.api.{DeleteUserDataTaskStep, UsernameChangeTaskStep}
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.util.Try

case class KeystoreMemoryModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[InMemoryKeystoreManager]).in(Scopes.SINGLETON)

    bind(classOf[KeystoreManager]).to(classOf[InMemoryKeystoreManager])
    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[PGPKeysUsernameChangeTaskStep])

    Multibinder.newSetBinder(binder(), classOf[DeleteUserDataTaskStep])
      .addBinding()
      .to(classOf[PGPKeysUserDeletionTaskStep])
  }
}

class InMemoryKeystoreManager (keystore: scala.collection.concurrent.Map[Username, Set[PublicKey]]) extends KeystoreManager {

  def this() = this(keystore = scala.collection.concurrent.TrieMap())

  override def save(username: Username, payload: Array[Byte]): Publisher[KeyId] =
    computeKeyId(payload)
      .fold(e => SMono.error(new IllegalArgumentException(e)), keyId => usernameExists(username)
        .fold(_ => create(username, keyId, payload), _ => update(username, keyId, payload)))

  override def listPublicKeys(username: Username): Publisher[PublicKey] =
    usernameExists(username)
      .fold(_ => SFlux.empty, _ => SFlux.fromIterable(keystore(username)))

  override def retrieveKey(username: Username, keyId: KeyId): Publisher[PublicKey] =
    usernameExists(username)
      .fold(_ => SMono.empty, _ => SMono.fromCallable[Set[PublicKey]](() => keystore(username))
        .flatMap(keys => find(keyId, keys)))

  override def delete(username: Username, keyId: KeyId): Publisher[Void] =
    usernameExists(username)
      .fold(_ => SMono.empty, _ => deleteKey(username, keyId))

  override def deleteAll(username: Username): Publisher[Void] =
    SMono.fromCallable(() => keystore.remove(username)).`then`()

  private def find(keyId: KeyId, keys: Set[PublicKey]): SMono[PublicKey] =
    keys.find(key => key.id.equals(keyId)).map(SMono.just)
      .getOrElse(SMono.empty)

  private def deleteKey(username: Username, keyId: KeyId): SMono[Void] =
    SMono.fromCallable[Set[PublicKey]](() => keystore(username))
    .flatMap(keys => checkKeyId(keys, keyId)
      .fold(_ => SMono.empty, key => deleteFromSet(username, keys, key)))

  private def deleteFromSet(username: Username, keys: Set[PublicKey], key: PublicKey): SMono[Void] =
    SMono.fromCallable(() => keystore.put(username, keys -- Set(key)))
      .`then`(SMono.empty)

  private def checkKeyId(keys: Set[PublicKey], keyId: KeyId): Either[IllegalArgumentException, PublicKey] =
    keys.find(k => k.id.equals(keyId))
      .map(Right(_))
      .getOrElse(Left(new IllegalArgumentException(s"Could not find key $keyId")))

  private def create(username: Username, keyId: KeyId, payload: Array[Byte]): Publisher[KeyId] =
    SMono.fromCallable(() => keystore.put(username, Set(PublicKey(keyId, payload))))
      .map[KeyId](_ => keyId)

  private def update(username: Username, keyId: KeyId, payload: Array[Byte]): Publisher[KeyId] = {
    val oldKeys = keystore(username)
    val newkey = PublicKey(keyId, payload)
    Option.when(oldKeys.contains(newkey))(SMono.just(keyId))
      .getOrElse(insertNewKey(username, oldKeys, newkey))
  }

  private def insertNewKey(username: Username, oldKeys: Set[PublicKey], newKey: PublicKey): SMono[KeyId] =
    SMono.fromCallable(() => keystore.put(username, oldKeys ++ Set(newKey)))
      .`then`(SMono.just(newKey.id))

  private def computeKeyId(payload: Array[Byte]): Either[Throwable, KeyId] =
    Try(Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
      .toEither
      .map(key => KeyId(BaseEncoding.base16().encode(key.getFingerprint)))

  private def usernameExists(username: Username): Either[Unit, Unit] =
    Option.when(keystore.contains(username))(Right())
      .getOrElse(Left())
}
