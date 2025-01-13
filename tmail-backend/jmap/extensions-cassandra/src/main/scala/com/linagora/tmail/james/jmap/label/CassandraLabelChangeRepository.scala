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
import jakarta.inject.Inject
import org.apache.james.jmap.api.change.{Limit, State}
import org.apache.james.jmap.api.exception.ChangeNotFoundException
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.scala.publisher.SMono

class CassandraLabelChangeRepository @Inject()(val dao: CassandraLabelChangeDAO) extends LabelChangeRepository {
  override def save(labelChange: LabelChange): Publisher[Void] =
    dao.insert(labelChange)

  override def getSinceState(accountId: AccountId, state: State, maxIdsToReturn: Option[Limit]): Publisher[LabelChanges] = {
    val maxIds: Int = maxIdsToReturn.getOrElse(DEFAULT_MAX_IDS_TO_RETURN).getValue

    if (state.equals(State.INITIAL)) {
      getAllChanges(accountId, maxIds)
    } else {
      getChangesSince(accountId, state, maxIds)
    }
  }

  override def getLatestState(accountId: AccountId): Publisher[State] =
    dao.selectLatestState(accountId)
      .switchIfEmpty(SMono.just(State.INITIAL))

  private def getAllChanges(accountId: AccountId, maxIds: Int): SMono[LabelChanges] =
    dao.selectAllChanges(accountId)
      .map(LabelChanges.from)
      .reduce(LabelChanges.initial())((change1, change2) => LabelChanges.merge(maxIds, change1, change2))

  private def getChangesSince(accountId: AccountId, state: State, maxIds: Int): SMono[LabelChanges] = {
    def throwChangeNotFoundException(state: State): Flux[LabelChange] =
      Flux.error(new ChangeNotFoundException(state, s"State '${state.getValue}' could not be found"))

    def fallbackToTheCurrentState(accountId: AccountId, state: State): SMono[LabelChange] =
      SMono.just(LabelChange(accountId = accountId, state = state))

    dao.selectChangesSince(accountId, state)
      .switchIfEmpty(throwChangeNotFoundException(state))
      .filter(!_.state.equals(state))
      .switchIfEmpty(fallbackToTheCurrentState(accountId, state))
      .map(LabelChanges.from)
      .reduce(LabelChanges.initial())((change1, change2) => LabelChanges.merge(maxIds, change1, change2))
  }
}
