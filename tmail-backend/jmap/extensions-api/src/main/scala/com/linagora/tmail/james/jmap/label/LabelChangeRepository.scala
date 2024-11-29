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