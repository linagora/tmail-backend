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
