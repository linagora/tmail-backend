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

import org.apache.james.jmap.api.change.Limit
import org.apache.james.jmap.core.{AccountId, UuidState}
import org.apache.james.jmap.method.WithAccountId

case class HasMoreChanges(value: Boolean) extends AnyVal

case class LabelChangesRequest(accountId: AccountId,
                               sinceState: UuidState,
                               maxChanges: Option[Limit]) extends WithAccountId
object LabelChangesResponse {
  def from(accountId: AccountId, oldState: UuidState,
           changes: LabelChanges): LabelChangesResponse =
    LabelChangesResponse(
      accountId = accountId,
      oldState = oldState,
      newState = UuidState.fromJava(changes.newState),
      hasMoreChanges = HasMoreChanges(changes.hasMoreChanges),
      created = changes.created,
      updated = changes.updated,
      destroyed = changes.destroyed)
}

case class LabelChangesResponse(accountId: AccountId,
                                oldState: UuidState,
                                newState: UuidState,
                                hasMoreChanges: HasMoreChanges,
                                created: Set[LabelId],
                                updated: Set[LabelId],
                                destroyed: Set[LabelId])