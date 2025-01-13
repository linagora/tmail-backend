/********************************************************************
 *  As a subpart of Twake Mail, this file is edited by Linagora.    *
 *                                                                  *
 *  https://twake-mail.com/                                         *
 *  https://linagora.com                                            *
 *                                                                  *
 *  This file is subject to The Affero Gnu Public License           *
 *  version 3.                                                      *
 *                                                                  *
 *  https://www.gnu.org/licenses/agpl-3.0.en.html                   *
 *                                                                  *
 *  This program is distributed in the hope that it will be         *
 *  useful, but WITHOUT ANY WARRANTY; without even the implied      *
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR         *
 *  PURPOSE. See the GNU Affero General Public License for          *
 *  more details.                                                   *
 ********************************************************************/

package com.linagora.tmail.encrypted

import org.apache.james.core.{Domain, Username}
import org.assertj.core.api.Assertions.{assertThat, assertThatCode, assertThatThrownBy}
import org.assertj.core.api.SoftAssertions.assertSoftly
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

trait KeystoreManagerContract {

  def keyStoreManager: KeystoreManager

  private lazy val DOMAIN: Domain = Domain.of("domain.tld")
  private lazy val BOB: Username = Username.fromLocalPartWithDomain("bob", DOMAIN)
  private lazy val ALICE: Username = Username.fromLocalPartWithDomain("alice", DOMAIN)

  @Test
  def saveShouldSucceedWhenUserHasNoKey(): Unit = {
    val payload: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()

    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block()

    assertThat(SMono.fromPublisher(keyStoreManager.retrieveKey(BOB, keyId)).block().hasSameContentAs(PublicKey(keyId, payload)))
      .isTrue
  }

  @Test
  def saveShouldUpdateWhenUserHasKeys(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val payload2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()

    val keyId1: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    val keyId2: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()

    val key1: PublicKey = PublicKey(keyId1, payload1)
    val key2: PublicKey = PublicKey(keyId2, payload2)

    assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().exists(_.hasSameContentAs(key1)))
        .isTrue
      softly.assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().exists(_.hasSameContentAs(key2)))
        .isTrue
    })
  }

  @Test
  def saveShouldReturnOldKeyIdWhenDuplicates(): Unit = {
    val payload: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()

    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block()
    val key: PublicKey = PublicKey(keyId, payload)

    assertSoftly(softly => {
      softly.assertThat(SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block())
          .isEqualTo(keyId)
      softly.assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().exists(_.hasSameContentAs(key)))
        .isTrue
    })
  }

  @Test
  def saveShouldThrowWhenInvalidPayload(): Unit = {
    val payload: Array[Byte] = "123456789".getBytes

    assertThatThrownBy(() => SMono.fromPublisher(keyStoreManager.save(BOB, payload)).block())
      .isInstanceOf(classOf[IllegalArgumentException])
  }

  @Test
  def listPublicKeysShouldListAllKeys(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val payload2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()

    val keyId1: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    val keyId2: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()

    val key1: PublicKey = PublicKey(keyId1, payload1)
    val key2: PublicKey = PublicKey(keyId2, payload2)

    assertSoftly(softly => {
      softly.assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().exists(_.hasSameContentAs(key1)))
        .isTrue
      softly.assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().exists(_.hasSameContentAs(key2)))
        .isTrue
    })
  }

  @Test
  def retrieveKeyShouldSucceed(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val payload2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()

    val keyId1: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()
    val key1: PublicKey = PublicKey(keyId1, payload1)

    assertThat(SMono.fromPublisher(keyStoreManager.retrieveKey(BOB, keyId1)).block().hasSameContentAs(key1))
      .isTrue
  }

  @Test
  def retrieveKeyShouldReturnEmptyWhenUnknownId(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val payload2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    val keyId2: KeyId = KeyId.fromPayload(payload2)

    assertThat(SMono.fromPublisher(keyStoreManager.retrieveKey(BOB, keyId2)).block())
      .isNull()
  }

  @Test
  def listPublicKeysShouldReturnEmptyWhenUserDoesNotExist(): Unit = {
    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteAllShouldSucceed(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val payload2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    SMono.fromPublisher(keyStoreManager.save(BOB, payload2)).block()

    SMono.fromPublisher(keyStoreManager.deleteAll(BOB)).block()

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteAllShouldLeaveOtherUserKeysIntact(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val payload2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()
    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(ALICE, payload2)).block()
    val key: PublicKey = PublicKey(keyId, payload2)

    SMono.fromPublisher(keyStoreManager.deleteAll(BOB)).block()

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(ALICE)).collectSeq().block().exists(_.hasSameContentAs(key)))
      .isTrue
  }

  @Test
  def deleteAllShouldSucceedWhenUserHasNoKey(): Unit = {
    assertThatCode(() => SMono.fromPublisher(keyStoreManager.deleteAll(BOB)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def deleteShouldSucceed(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()

    val keyId: KeyId = SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()

    SMono.fromPublisher(keyStoreManager.delete(BOB, keyId)).block()

    assertThat(SFlux.fromPublisher(keyStoreManager.listPublicKeys(BOB)).collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def deleteShouldSucceedWhenUserHasNoKey(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
    val keyId: KeyId = KeyId.fromPayload(payload1)

    assertThatCode(() => SMono.fromPublisher(keyStoreManager.delete(BOB, keyId)).block())
      .doesNotThrowAnyException()
  }

  @Test
  def deleteShouldBeIdempotent(): Unit = {
    val payload1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()

    SMono.fromPublisher(keyStoreManager.save(BOB, payload1)).block()

    val keyId: KeyId = KeyId.fromPayload(ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes())

    assertThatCode(() => SMono.fromPublisher(keyStoreManager.delete(BOB, keyId)).block())
      .doesNotThrowAnyException()
  }
}
