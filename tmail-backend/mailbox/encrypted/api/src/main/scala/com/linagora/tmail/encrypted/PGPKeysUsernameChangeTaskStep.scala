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

import jakarta.inject.Inject
import org.apache.james.core.Username
import org.apache.james.user.api.UsernameChangeTaskStep
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

class PGPKeysUsernameChangeTaskStep @Inject()(keystoreManager: KeystoreManager) extends UsernameChangeTaskStep {
  override def name(): UsernameChangeTaskStep.StepName = new UsernameChangeTaskStep.StepName("PGPKeysUsernameChangeTaskStep")

  override def priority(): Int = 6

  override def changeUsername(oldUsername: Username, newUsername: Username): Publisher[Void] =
    SFlux(keystoreManager.listPublicKeys(oldUsername))
      .flatMap(publicKey => SMono(keystoreManager.save(newUsername, publicKey.key))
      .`then`(SMono(keystoreManager.delete(oldUsername, publicKey.id))))
}
