package com.linagora.tmail.james.jmap.label

import com.google.common.collect.{HashBasedTable, Table, Tables}
import com.linagora.tmail.james.jmap.model.LabelId
import org.apache.james.jmap.api.change.{JmapChange, Limit, State}
import org.apache.james.jmap.api.exception.ChangeNotFoundException
import org.apache.james.jmap.api.model.AccountId
import org.reactivestreams.Publisher
import reactor.core.scala.publisher.{SFlux, SMono}

import java.time.{Clock, Instant}
import javax.inject.Named

trait LabelChangeRepository {
  def save(labelChange: LabelChange): Publisher[Void]

  def getSinceState(accountId: AccountId, state: State, maxIdsToReturn: Option[Limit] = None): Publisher[LabelChanges]

  def getLatestState(accountId: AccountId): Publisher[State]

}

case class LabelChange(accountId: AccountId,
                       created: Set[LabelId] = Set(),
                       updated: Set[LabelId] = Set(),
                       destroyed: Set[LabelId] = Set(),
                       state: State) extends JmapChange {
  override def getAccountId: AccountId = accountId
}

object LabelChanges {
  def from(labelChange: LabelChange): LabelChanges = LabelChanges(
    created = labelChange.created,
    updated = labelChange.updated,
    destroyed = labelChange.destroyed,
    newState = labelChange.state)

  def initial(): LabelChanges = LabelChanges(
    created = Set(),
    updated = Set(),
    destroyed = Set(),
    newState = State.INITIAL)

  def merge(limit: Int, change1: LabelChanges, change2: LabelChanges): LabelChanges =
    change1.canAppendMoreItem match {
      case false => change1
      case true if change1.newState.equals(State.INITIAL) => change2
      case true =>
        val createdTemp: Set[LabelId] = (change1.created ++ change2.created).diff(change2.destroyed)
        val updatedTemp: Set[LabelId] = (change1.updated ++ (change2.updated.diff(createdTemp))).diff(change2.destroyed)
        val destroyedTemp: Set[LabelId] = change1.destroyed ++ (change2.destroyed.diff(change1.created))
        if (createdTemp.size + updatedTemp.size + destroyedTemp.size > limit) {
          change1.copy(hasMoreChanges = true, canAppendMoreItem = false)
        } else {
          change1.copy(created = createdTemp,
            updated = updatedTemp,
            destroyed = destroyedTemp,
            newState = change2.newState)
        }
    }
}

case class LabelChanges(created: Set[LabelId] = Set(),
                        updated: Set[LabelId] = Set(),
                        destroyed: Set[LabelId] = Set(),
                        hasMoreChanges: Boolean = false,
                        newState: State,
                        private val canAppendMoreItem: Boolean = true) {
  def getAllChanges: Set[LabelId] = created ++ updated ++ destroyed
}

case class MemoryLabelChangeRepository(@Named("labelChangeDefaultLimit") defaultLimit: Limit, clock: Clock) extends LabelChangeRepository {

  import scala.jdk.CollectionConverters._

  private val orderingNewerFirst: Ordering[(Instant, LabelChange)] = Ordering.by[(Instant, LabelChange), Instant](_._1)
  private val tableStore: Table[AccountId, Instant, LabelChange] = Tables.synchronizedTable(HashBasedTable.create())

  override def save(labelChange: LabelChange): Publisher[Void] =
    SMono.fromCallable(() => tableStore.put(labelChange.accountId, clock.instant(), labelChange))
      .`then`()

  override def getSinceState(accountId: AccountId, state: State, maxIdsToReturn: Option[Limit]): SMono[LabelChanges] = {
    val limit: Int = maxIdsToReturn.getOrElse(defaultLimit).getValue
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