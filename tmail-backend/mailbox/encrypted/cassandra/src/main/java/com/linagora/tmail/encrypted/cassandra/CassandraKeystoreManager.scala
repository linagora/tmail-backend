package com.linagora.tmail.encrypted.cassandra

import java.io.ByteArrayInputStream

import com.google.common.io.BaseEncoding
import com.google.inject.multibindings.Multibinder
import com.google.inject.{AbstractModule, Scopes}
import com.linagora.tmail.encrypted.cassandra.table.CassandraKeystoreModule
import com.linagora.tmail.encrypted.{KeyId, KeystoreManager, PGPKeysUsernameChangeTaskStep, PublicKey}
import com.linagora.tmail.pgp.Encrypter
import javax.inject.Inject
import org.apache.james.backends.cassandra.components.CassandraModule
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.SMono

import scala.util.Try

case class KeystoreCassandraModule() extends AbstractModule {
  override def configure(): Unit = {
    bind(classOf[CassandraKeystoreManager]).in(Scopes.SINGLETON)
    bind(classOf[CassandraKeystoreDAO]).in(Scopes.SINGLETON)

    bind(classOf[KeystoreManager]).to(classOf[CassandraKeystoreManager])

    Multibinder.newSetBinder(binder, classOf[CassandraModule])
      .addBinding()
      .toInstance(CassandraKeystoreModule.MODULE)

    Multibinder.newSetBinder(binder(), classOf[UsernameChangeTaskStep])
      .addBinding()
      .to(classOf[PGPKeysUsernameChangeTaskStep])
  }
}

class CassandraKeystoreManager @Inject()(cassandraKeystoreDAO: CassandraKeystoreDAO) extends KeystoreManager {
  override def save(username: Username, payload: Array[Byte]): Publisher[KeyId] =
    computeKeyId(payload)
      .fold(e => SMono.error(new IllegalArgumentException(e)),
        keyId => cassandraKeystoreDAO.insert(username, PublicKey(keyId, payload))
          .`then`(SMono.just(keyId)))

  override def listPublicKeys(username: Username): Publisher[PublicKey] =
    cassandraKeystoreDAO.getAllKeys(username)

  override def retrieveKey(username: Username, id: KeyId): Publisher[PublicKey] =
    cassandraKeystoreDAO.getKey(username, id)

  override def delete(username: Username, id: KeyId): Publisher[Void] =
    cassandraKeystoreDAO.deleteKey(username, id)

  override def deleteAll(username: Username): Publisher[Void] =
    cassandraKeystoreDAO.deleteAllKeys(username)

  private def computeKeyId(payload: Array[Byte]): Either[Throwable, KeyId] =
    Try(Encrypter.readPublicKey(new ByteArrayInputStream(payload)))
      .toEither
      .map(key => KeyId(BaseEncoding.base16().encode(key.getFingerprint)))
}
