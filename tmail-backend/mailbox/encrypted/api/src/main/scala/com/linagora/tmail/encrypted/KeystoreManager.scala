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

import com.google.common.io.BaseEncoding
import org.apache.james.core.Username
import org.reactivestreams.Publisher

object KeyId {
  def fromPayload(payload: Array[Byte]): KeyId = KeyId(BaseEncoding.base16().encode(payload))
}

case class KeyId(value: String) extends AnyVal

case class PublicKey(id: KeyId, key: Array[Byte]) {
  def hasSameContentAs(publicKey: PublicKey): Boolean =
    id.equals(publicKey.id) && key.sameElements(publicKey.key)
}

trait KeystoreManager {

  def save(username: Username, payload: Array[Byte]): Publisher[KeyId]

  def listPublicKeys(username: Username): Publisher[PublicKey]

  def retrieveKey(username: Username, id: KeyId): Publisher[PublicKey]

  def delete(username: Username, id: KeyId): Publisher[Void]

  def deleteAll(username: Username): Publisher[Void]
}
