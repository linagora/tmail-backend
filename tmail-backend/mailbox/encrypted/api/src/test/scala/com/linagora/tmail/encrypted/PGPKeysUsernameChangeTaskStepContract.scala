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

import com.linagora.tmail.encrypted.PGPKeysUsernameChangeTaskStepContract.{NEW_USERNAME, OLD_USERNAME, PUBLIC_KEY_1, PUBLIC_KEY_2}
import org.apache.james.core.Username
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import reactor.core.scala.publisher.{SFlux, SMono}

import scala.jdk.CollectionConverters._

object PGPKeysUsernameChangeTaskStepContract {
  val OLD_USERNAME: Username = Username.of("alice@linagora.com")
  val NEW_USERNAME: Username = Username.of("bob@linagora.com")

  val PUBLIC_KEY_1: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg.pub").readAllBytes()
  val PUBLIC_KEY_2: Array[Byte] = ClassLoader.getSystemClassLoader.getResourceAsStream("gpg2.pub").readAllBytes()
}

trait PGPKeysUsernameChangeTaskStepContract {
  def keyStoreManager: KeystoreManager

  def testee: PGPKeysUsernameChangeTaskStep

  @Test
  def shouldMigratePublicKey(): Unit = {
    SMono(keyStoreManager.save(OLD_USERNAME, PUBLIC_KEY_1)).block()
    SMono(keyStoreManager.save(OLD_USERNAME, PUBLIC_KEY_2)).block()

    SMono(testee.changeUsername(OLD_USERNAME, NEW_USERNAME)).block()

    assertThat(SFlux(keyStoreManager.listPublicKeys(NEW_USERNAME))
      .map(_.key)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(PUBLIC_KEY_1, PUBLIC_KEY_2)
  }

  @Test
  def shouldRemovePublicKeyFromOldAccount(): Unit = {
    SMono(keyStoreManager.save(OLD_USERNAME, PUBLIC_KEY_1)).block()
    SMono(keyStoreManager.save(OLD_USERNAME, PUBLIC_KEY_2)).block()

    SMono(testee.changeUsername(OLD_USERNAME, NEW_USERNAME)).block()

    assertThat(SFlux(keyStoreManager.listPublicKeys(OLD_USERNAME))
      .collectSeq().block().asJava)
      .isEmpty()
  }

  @Test
  def shouldNotOverrideExistedPublicKeyOnNewAccount(): Unit = {
    SMono(keyStoreManager.save(OLD_USERNAME, PUBLIC_KEY_1)).block()
    SMono(keyStoreManager.save(NEW_USERNAME, PUBLIC_KEY_2)).block()

    SMono(testee.changeUsername(OLD_USERNAME, NEW_USERNAME)).block()

    assertThat(SFlux(keyStoreManager.listPublicKeys(NEW_USERNAME))
      .map(_.key)
      .collectSeq().block().asJava)
      .containsExactlyInAnyOrder(PUBLIC_KEY_1, PUBLIC_KEY_2)
  }
}
