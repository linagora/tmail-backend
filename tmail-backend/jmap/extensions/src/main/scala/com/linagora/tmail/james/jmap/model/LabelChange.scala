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