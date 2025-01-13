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

import java.time.{Clock, Instant}

import com.google.common.collect.{HashBasedTable, Table, Tables}
import com.linagora.tmail.james.jmap.label.LabelChangeRepository.DEFAULT_MAX_IDS_TO_RETURN
import com.linagora.tmail.james.jmap.model.{LabelChange, LabelChanges}
import jakarta.inject.Inject
import org.apache.james.jmap.api.change.{Limit, State}
import org.apache.james.jmap.api.exception.ChangeNotFoundException
import org.apache.james.jmap.api.model
import org.apache.james.jmap.api.model.{AccountId, TypeName}
import org.apache.james.jmap.core.UuidState
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

case object LabelTypeName extends TypeName {
  override val asString: String = "Label"

  override def parse(string: String): Option[TypeName] = string match {
    case LabelTypeName.asString => Some(LabelTypeName)
    case _ => None
  }

  override def parseState(string: String): Either[IllegalArgumentException, model.State] = UuidState.parse(string)
}

case class MemoryLabelChangeRepository @Inject()(clock: Clock) extends LabelChangeRepository {

  import scala.jdk.CollectionConverters._

  private val orderingNewerFirst: Ordering[(Instant, LabelChange)] = Ordering.by[(Instant, LabelChange), Instant](_._1)
  private val tableStore: Table[AccountId, Instant, LabelChange] = Tables.synchronizedTable(HashBasedTable.create())

  override def save(labelChange: LabelChange): Publisher[Void] =
    SMono.fromCallable(() => tableStore.put(labelChange.accountId, clock.instant(), labelChange))
      .`then`()

  override def getSinceState(accountId: AccountId, state: State, maxIdsToReturn: Option[Limit]): SMono[LabelChanges] = {
    val limit: Int = maxIdsToReturn.getOrElse(DEFAULT_MAX_IDS_TO_RETURN).getValue
    getSinceState(accountId, state)
      .sort(orderingNewerFirst)
      .map(_._2)
      .switchIfEmpty(SMono.just(LabelChange(accountId = accountId, state = state)))
      .map(LabelChanges.from)
      .reduce(LabelChanges.initial())((change1, change2) => LabelChanges.merge(limit, change1, change2))
  }

  override def getLatestState(accountId: AccountId): SMono[State] =
    findByAccountId(accountId)
      .max(orderingNewerFirst)
      .flatMap(SMono.justOrEmpty)
      .map(_._2.state)
      .switchIfEmpty(SMono.just(State.INITIAL))

  private def findByAccountId(accountId: AccountId): SFlux[(Instant, LabelChange)] =
    SFlux.fromIterable(tableStore.row(accountId).asScala)

  private def getSinceState(accountId: AccountId, state: State): SFlux[(Instant, LabelChange)] =
    state match {
      case State.INITIAL => findByAccountId(accountId)
      case _ => SFlux.fromIterable(tableStore.row(accountId).asScala)
        .filter(_._2.state.getValue == state.getValue)
        .next()
        .switchIfEmpty(SMono.error[(Instant, LabelChange)](new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue))))
        .flatMapMany(mileStone => findByAccountId(accountId)
          .filter(_._1.isAfter(mileStone._1)))
    }
}