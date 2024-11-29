package com.linagora.tmail.james.jmap.model

import java.util.function.Supplier

import org.apache.james.jmap.api.change.{JmapChange, State}
import org.apache.james.jmap.api.model.AccountId

case class LabelChange(accountId: AccountId,
                       created: Set[LabelId] = Set(),
                       updated: Set[LabelId] = Set(),
                       destroyed: Set[LabelId] = Set(),
                       state: State) extends JmapChange {
  override def getAccountId: AccountId = accountId

  override def isNoop: Boolean = created.isEmpty && updated.isEmpty && destroyed.isEmpty

  override def forSharee(accountId: AccountId, state: Supplier[State]): JmapChange =
    LabelChange(accountId = accountId,
      created = created,
      updated = updated,
      destroyed = destroyed,
      state = state.get())
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
        val updatedTemp: Set[LabelId] = (change1.updated ++ change2.updated.diff(createdTemp)).diff(change2.destroyed)
        val destroyedTemp: Set[LabelId] = change1.destroyed ++ change2.destroyed.diff(change1.created)
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