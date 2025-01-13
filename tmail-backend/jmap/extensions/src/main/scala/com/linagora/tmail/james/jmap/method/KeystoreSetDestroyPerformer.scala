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

package com.linagora.tmail.james.jmap.method

import com.linagora.tmail.encrypted.{KeyId, KeystoreManager}
import com.linagora.tmail.james.jmap.model.KeystoreSetRequest
import jakarta.inject.Inject
import org.apache.james.mailbox.MailboxSession
import reactor.core.scala.publisher.{SFlux, SMono}

object DestroyResults {
  def merge(currentResults: DestroyResults, entry: DestroyResult): DestroyResults =
    entry match {
      case success: DestroySuccess => DestroyResults(currentResults.retrieveDestroyed ++ List(success))
      case failure: DestroyFailure => DestroyResults(currentResults.retrieveNotDestroyed ++ List(failure))
    }

  def empty: DestroyResults = DestroyResults(List())
}

case class DestroyResults(values: List[DestroyResult]) {
  def retrieveDestroyed: List[DestroySuccess] = values.flatMap {
    case value: DestroySuccess => Some(value)
    case _ => None
  }

  def retrieveNotDestroyed: List[DestroyFailure] = values.flatMap {
    case value: DestroyFailure => Some(value)
    case _ => None
  }
}

sealed trait DestroyResult
case class DestroySuccess(id: KeyId) extends DestroyResult
case class DestroyFailure(id: KeyId, throwable: Throwable) extends DestroyResult

class KeystoreSetDestroyPerformer @Inject()(keystore: KeystoreManager) {

  def destroy(mailboxSession: MailboxSession, request: KeystoreSetRequest): SMono[DestroyResults] =
    SFlux.fromIterable(request.destroy.getOrElse(List()))
        .flatMap(id => destroy(mailboxSession, id))
      .fold(DestroyResults.empty)((result: DestroyResults, res: DestroyResult) => DestroyResults.merge(result, res))

  private def destroy(mailboxSession: MailboxSession, id: KeyId): SMono[DestroyResult] =
    SMono.fromPublisher(keystore.delete(mailboxSession.getUser, KeyId(id.value)))
      .`then`(SMono.just(DestroySuccess(id)))
      .onErrorResume(e => SMono.just(DestroyFailure(id, e)))
}
