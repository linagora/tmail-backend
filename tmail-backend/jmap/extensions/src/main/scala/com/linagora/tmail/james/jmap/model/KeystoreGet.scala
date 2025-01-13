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

package com.linagora.tmail.james.jmap.model

import com.linagora.tmail.encrypted.{KeyId, PublicKey}
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class KeystoreGetRequest(accountId: AccountId,
                              ids: Option[Set[KeyId]]) extends WithAccountId {
  def notFound(actualIds: Set[KeyId]): Set[KeyId] = ids
    .map(requestedIds => requestedIds.diff(actualIds))
    .getOrElse(Set[KeyId]())
}

case class KeystoreGetResponse(accountId: AccountId,
                               state: UuidState,
                               list: List[PublicKey],
                               notFound: Set[KeyId])
