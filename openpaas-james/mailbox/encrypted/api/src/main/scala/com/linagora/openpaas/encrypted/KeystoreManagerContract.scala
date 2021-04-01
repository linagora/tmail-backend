package com.linagora.openpaas.encrypted

import org.apache.james.core.{Domain, Username}
import org.assertj.core.api.Assertions.{assertThat, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

trait KeystoreManagerContract {

  def keyStoreManager: KeystoreManager

  def generateKey: KeyId

  private lazy val DOMAIN: Domain = Domain.of("domain.tld")
  private lazy val BOB: Username = Username.fromLocalPartWithDomain("bob", DOMAIN)
  private lazy val ALICE: Username = Username.fromLocalPartWithDomain("alice", DOMAIN)

  @Test
  def saveShouldSucceedWhenUserHasNoKey(): Unit = {
    val payload: Array[Byte] = "123456789".getBytes

    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block()

    assertThat(SMono.fromPublisher(keyStoreManager.retrieveKey(BOB, keyId)).block())
      .isEqualTo(PublicKey(keyId, payload))
  }

  @Test
  def saveShouldUpdateWhenUserHasKeys(): Unit = {
    val payload: Array[Byte] = "123456789".getBytes
    val payload2: Array[Byte] = "123456788".getBytes

    val keyId1: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block()
    val keyId2: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()

    val key1: PublicKey = PublicKey(keyId1, payload)
    val key2: PublicKey = PublicKey(keyId2, payload2)

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .containsExactly(key1, key2)
  }

  @Test
  def saveShouldReturnOldKeyIdWhenDuplicates(): Unit = {
    val payload: Array[Byte] = "123456789".getBytes

    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block()
    val key: PublicKey = PublicKey(keyId, payload)

    assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block())
          .isEqualTo(keyId)
      softly.assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
        .containsExactly(key)
    })
  }

  @Test
  def listPublicKeysShouldListAllKeys(): Unit = {
    val payload1: Array[Byte] = "123456789".getBytes
    val payload2: Array[Byte] = "123456788".getBytes
    val payload3: Array[Byte] = "123456787".getBytes

    val keyId1: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    val keyId2: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()
    val keyId3: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload3)).block()

    val key1: PublicKey = PublicKey(keyId1, payload1)
    val key2: PublicKey = PublicKey(keyId2, payload2)
    val key3: PublicKey = PublicKey(keyId3, payload3)

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .containsExactly(key1, key2, key3)
  }

  @Test
  def retrieveKeyShouldSucceed(): Unit = {
    val payload1: Array[Byte] = "123456789".getBytes
    val payload2: Array[Byte] = "123456788".getBytes
    val payload3: Array[Byte] = "123456787".getBytes

    val keyId1: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload3)).block()

    val key1: PublicKey = PublicKey(keyId1, payload1)

    assertThat(SMono.fromPublisher(keyStoreManager.retrieveKey(BOB, keyId1)).block())
      .isEqualTo(key1)
  }

  @Test
  def listPublicKeysShouldReturnEmptyWhenUserDoesNotExist(): Unit = {
    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteAllShouldSucceed(): Unit = {
    val payload1: Array[Byte] = "123456789".getBytes
    val payload2: Array[Byte] = "123456788".getBytes
    val payload3: Array[Byte] = "123456787".getBytes

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload3)).block()

    SMono.fromPublisher(keyStoreManager.deleteAll(BOB)).block()

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteAllShouldLeaveOtherUserKeysIntact(): Unit = {
    val payload1: Array[Byte] = "123456789".getBytes
    val payload2: Array[Byte] = "123456788".getBytes
    val payload3: Array[Byte] = "123456787".getBytes

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()
    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(ALICE, payload3)).block()
    val key: PublicKey = PublicKey(keyId, payload3)

    SMono.fromPublisher(keyStoreManager.deleteAll(BOB)).block()

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(ALICE)).collectSeq().block().asJava)
      .contains(key)
  }

  @Test
  def deleteShouldSucceed(): Unit = {
    val payload1: Array[Byte] = "123456789".getBytes

    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()

    SMono.fromPublisher(keyStoreManager.delete(BOB, keyId)).block()

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteShouldThrowWhenUserHasNoKey(): Unit = {
    val keyId: KeyId = KeyId.generate()

    assertThatThrownBy(() => SMono.fromPublisher(keyStoreManager.delete(BOB, keyId)).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def deleteShouldThrowWhenKeyIdNotFound(): Unit = {
    val payload1: Array[Byte] = "123456789".getBytes

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()

    val keyId: KeyId = KeyId.generate()

    assertThatThrownBy(() => SMono.fromPublisher(keyStoreManager.delete(BOB, keyId)).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }
}
