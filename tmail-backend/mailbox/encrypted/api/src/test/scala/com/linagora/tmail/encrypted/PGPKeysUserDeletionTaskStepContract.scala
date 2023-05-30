package com.linagora.tmail.encrypted

import com.linagora.tmail.encrypted.PGPKeysUserDeletionTaskStepContract.{ALICE, BOB, PUBLIC_KEY_1, PUBLIC_KEY_2}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object PGPKeysUserDeletionTaskStepContract {
  private val ALICE: Username = Username.of("alice@linagora.com")
  private val BOB: Username = Username.of("bob@linagora.com")

  private val PUBLIC_KEY_1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
  private val PUBLIC_KEY_2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()
}

trait PGPKeysUserDeletionTaskStepContract {
  def keyStoreManager: KeystoreManager

  def testee: PGPKeysUserDeletionTaskStep

  @Test
  def shouldRemovePublicKeys(): Unit = {
    SMono(keyStoreManager.save(ALICE, PUBLIC_KEY_1)).block()
    SMono(keyStoreManager.save(ALICE, PUBLIC_KEY_2)).block()

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux(keyStoreManager.listPublicKeys(ALICE))
      .collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotRemovePublicKeysOfOtherUsers(): Unit = {
    SMono(keyStoreManager.save(ALICE, PUBLIC_KEY_1)).block()
    SMono(keyStoreManager.save(BOB, PUBLIC_KEY_2)).block()

    SMono(testee.deleteUserData(ALICE)).block()

    assertThat(SFlux(keyStoreManager.listPublicKeys(BOB))
      .map(_.key)
      .collectSeq().block().asJava)
      .containsOnly(PUBLIC_KEY_2)
  }
}
