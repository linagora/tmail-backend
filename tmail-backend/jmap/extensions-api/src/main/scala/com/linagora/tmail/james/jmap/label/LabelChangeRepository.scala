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

package com.linagora.tmail.james.jmap.label

import com.linagora.tmail.james.jmap.label.LabelChangeRepository.DEFAULT_MAX_IDS_TO_RETURN
import com.linagora.tmail.james.jmap.model.{LabelChange, LabelChanges}
import org.apache.james.jmap.api.change.{Limit, State}
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher

object LabelChangeRepository {
  val DEFAULT_MAX_IDS_TO_RETURN : Limit = Limit.of(256)
}

trait LabelChangeRepository {
  def save(labelChange: LabelChange): Publisher[Void]

  def getSinceState(accountId: AccountId, state: State, maxIdsToReturn: Option[Limit] = Some(DEFAULT_MAX_IDS_TO_RETURN)): Publisher[LabelChanges]

  def getLatestState(accountId: AccountId): Publisher[State]
}